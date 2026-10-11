//! 验证真实候选、部分上屏、旧帧拒绝与私密输入的落盘行为。
use std::path::PathBuf;
use std::sync::atomic::{AtomicUsize, Ordering};

use qingjian_core::{Engine, Language};
use qingjian_dictionary::Dictionary;
use qingjian_translate::Glossary;

use super::Session;
use crate::command::Command;

fn session() -> Session {
    let dictionary = Dictionary::parse(
        "开发\tkai fa\t9000\n开发者\tkai fa zhe\t8000\n者\tzhe\t7000\n你好\tni hao\t9000\n",
    )
    .unwrap();
    let glossary =
        Glossary::parse(Language::English, "开发\tv. develop\n你好\tint. hello\n").unwrap();
    Session::new(Engine::new(dictionary).with_translator(Box::new(glossary)))
}

#[test]
fn translated_candidate_commit_consumes_only_its_pinyin() {
    let mut s = session();
    let frame = s
        .dispatch(Command::Input {
            text: "kaifazhe".into(),
        })
        .unwrap();
    let index = frame
        .candidates
        .iter()
        .position(|c| c.text == "开发")
        .unwrap();
    assert_eq!(frame.candidates[index].translation, "develop");
    let frame = s
        .dispatch(Command::Select {
            index,
            revision: frame.revision,
        })
        .unwrap();
    assert_eq!(frame.committed, "开发");
    assert_eq!(frame.preedit, "zhe");
    assert_eq!(s.dispatch(Command::Space).unwrap().committed, "者");
}

#[test]
fn stale_candidate_cannot_select_a_new_word() {
    let mut s = session();
    let first = s
        .dispatch(Command::Input {
            text: "kaifa".into(),
        })
        .unwrap();
    s.dispatch(Command::Reset { private: false }).unwrap();
    s.dispatch(Command::Input {
        text: "nihao".into(),
    })
    .unwrap();
    let frame = s
        .dispatch(Command::Select {
            index: 0,
            revision: first.revision,
        })
        .unwrap();
    assert_eq!(frame.committed, "");
    assert_eq!(frame.preedit, "nihao");
}

#[test]
fn backspace_distinguishes_preedit_from_editor_deletion() {
    let mut s = session();
    s.dispatch(Command::Input { text: "n".into() }).unwrap();
    assert!(!s.dispatch(Command::Backspace).unwrap().delete_backward);
    assert!(s.dispatch(Command::Backspace).unwrap().delete_backward);
}

#[test]
fn raw_and_punctuation_preserve_text() {
    let mut s = session();
    s.dispatch(Command::Input {
        text: "kaifa".into(),
    })
    .unwrap();
    assert_eq!(
        s.dispatch(Command::Literal { text: ",".into() })
            .unwrap()
            .committed,
        "开发，"
    );
    s.dispatch(Command::Input {
        text: "nihao".into(),
    })
    .unwrap();
    assert_eq!(s.dispatch(Command::Raw).unwrap().committed, "nihao");
}

#[test]
fn rejects_invalid_or_unbounded_input_without_changing_preedit() {
    let mut s = session();
    assert!(
        s.dispatch(Command::Input {
            text: "你好".into()
        })
        .is_err()
    );
    assert!(
        s.dispatch(Command::Input {
            text: "a".repeat(97)
        })
        .is_err()
    );
    assert!(s.dispatch(Command::Literal { text: "\n".into() }).is_err());
    assert_eq!(s.dispatch(Command::Raw).unwrap().committed, "");
}

static NEXT_TEMP: AtomicUsize = AtomicUsize::new(0);

struct TempDirectory(PathBuf);

impl TempDirectory {
    fn new() -> Self {
        let path = std::env::temp_dir().join(format!(
            "qingjian-android-{}-{}",
            std::process::id(),
            NEXT_TEMP.fetch_add(1, Ordering::Relaxed)
        ));
        std::fs::create_dir_all(&path).unwrap();
        Self(path)
    }
}

impl Drop for TempDirectory {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.0);
    }
}

#[test]
fn normal_learning_survives_restart_private_input_does_not_change_files() {
    let dir = TempDirectory::new();
    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../assets/sample");
    let mut s = Session::open(&root, &dir.0).unwrap();
    s.dispatch(Command::Input {
        text: "kaifa".into(),
    })
    .unwrap();
    let normal = s.dispatch(Command::Space).unwrap();
    assert_eq!(normal.committed, "开发");
    drop(s);
    let before = std::fs::read(dir.0.join("user.tsv")).unwrap();
    assert!(String::from_utf8_lossy(&before).contains("开发"));
    let mut s = Session::open(&root, &dir.0).unwrap();
    s.dispatch(Command::Reset { private: true }).unwrap();
    s.dispatch(Command::Input {
        text: "nihao".into(),
    })
    .unwrap();
    assert_eq!(s.dispatch(Command::Space).unwrap().committed, "你好");
    drop(s);
    assert_eq!(std::fs::read(dir.0.join("user.tsv")).unwrap(), before);
}
