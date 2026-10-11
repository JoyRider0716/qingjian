//! Android 会话：装配已有引擎，并保留每个显示帧对应的真实候选。
use std::path::Path;

use qingjian_core::{Candidate, Engine, Language};
use qingjian_dictionary::{Dictionary, WordList};
use qingjian_learning::FrequencyLearner;
use qingjian_translate::Glossary;

use crate::command::Command;
use crate::error::AndroidError;
use crate::frame::{DisplayCandidate, Frame};

const MAX_INPUT: usize = 96;
const MAX_CANDIDATES: usize = 40;

pub struct Session {
    engine: Engine,

    candidates: Vec<Candidate>,

    revision: u64,
}

impl Session {
    pub fn open(resources: &Path, user: &Path) -> Result<Self, AndroidError> {
        std::fs::create_dir_all(user)?;
        let dictionary = Dictionary::from_path(resources.join("dict.tsv"))?;
        let glossary = Glossary::from_path(Language::English, resources.join("glossary-en.tsv"))?;
        // 不可读的数据不能被空表覆盖；读失败时与桌面端一样仅在内存里学习。
        let learner = FrequencyLearner::from_path(user.join("user.tsv")).unwrap_or_default();
        let mut engine = Engine::new(dictionary)
            .with_translator(Box::new(glossary))
            .with_learner(Box::new(learner));
        let english = resources.join("english.tsv");
        if english.is_file() {
            engine = engine.with_english(WordList::from_path(english)?);
        }
        Ok(Self::new(engine))
    }

    fn new(engine: Engine) -> Self {
        Self {
            engine,
            candidates: Vec::new(),
            revision: 0,
        }
    }

    pub fn dispatch(&mut self, command: Command) -> Result<Frame, AndroidError> {
        let mut committed = String::new();
        let mut delete_backward = false;
        match command {
            Command::Input { text } => {
                if text.is_empty() || !text.chars().all(|c| c.is_ascii_lowercase() || c == '\'') {
                    return Err(AndroidError::Input("expected lowercase pinyin letters"));
                }
                if self.engine.composition().text().len() + text.len() > MAX_INPUT {
                    return Err(AndroidError::Input("composition exceeds 96 letters"));
                }
                for c in text.chars() {
                    self.engine.push(c);
                }
            }
            Command::Backspace => {
                delete_backward = !self.engine.backspace();
                if delete_backward {
                    self.engine.note_backspace();
                }
            }
            Command::Select { index, revision } => {
                // 回包期间旧候选仍可能被点到；旧帧不得提交新帧的同一槽位。
                if revision == self.revision
                    && let Some(candidate) = self.candidates.get(index)
                {
                    committed = self.engine.commit(candidate);
                    self.engine.flush_learning();
                }
            }
            Command::Space => {
                committed = if let Some(candidate) = self.candidates.first() {
                    self.engine.commit(candidate)
                } else if !self.engine.composition().is_empty() {
                    self.engine.take_raw()
                } else {
                    self.engine.note_passthrough(' ');
                    " ".to_owned()
                };
                self.engine.flush_learning();
            }
            Command::Raw => {
                committed = self.engine.take_raw();
                self.engine.flush_learning();
            }
            Command::Literal { text } => {
                if text.chars().count() != 1 || text.chars().any(char::is_control) {
                    return Err(AndroidError::Input("expected one printable character"));
                }
                // 先提交当前候选；短词候选留下的拼音原样提交，避免符号截断组句。
                if let Some(candidate) = self.candidates.first() {
                    committed.push_str(&self.engine.commit(candidate));
                }
                committed.push_str(&self.engine.take_raw());
                let c = text.chars().next().expect("validated character");
                if let Some(punctuation) = self.engine.punctuate(c) {
                    committed.push_str(punctuation);
                } else {
                    self.engine.note_passthrough(c);
                    committed.push(c);
                }
                self.engine.flush_learning();
            }
            Command::Reset { private } => {
                self.engine.discard_input();
                self.engine.break_chain();
                self.engine.set_private(private);
                self.engine.flush_learning();
            }
            Command::Flush => self.engine.flush_learning(),
        }
        self.frame(committed, delete_backward)
    }

    fn frame(&mut self, committed: String, delete_backward: bool) -> Result<Frame, AndroidError> {
        self.revision = self
            .revision
            .checked_add(1)
            .ok_or(AndroidError::Input("revision overflow"))?;
        self.candidates.clear();
        if !self.engine.composition().is_empty()
            && let Ok(mut query) = self.engine.query()
        {
            query.candidates.items.truncate(MAX_CANDIDATES);
            self.engine.annotate(&mut query.candidates);
            self.candidates = query.candidates.items;
        }
        Ok(Frame {
            revision: self.revision,
            preedit: self.engine.composition().text().to_owned(),
            committed,
            delete_backward,
            candidates: self
                .candidates
                .iter()
                .map(|candidate| DisplayCandidate {
                    text: candidate.text.clone(),
                    translation: candidate
                        .translation
                        .as_ref()
                        .and_then(|translation| translation.senses().first())
                        .map_or_else(String::new, |sense| sense.text.clone()),
                })
                .collect(),
        })
    }
}

impl Drop for Session {
    fn drop(&mut self) {
        self.engine.flush_learning();
    }
}

#[cfg(test)]
mod tests;
