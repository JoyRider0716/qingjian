//! Android 按键与生命周期命令；候选选择携带展示版本。
use serde::Deserialize;

#[derive(Debug, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case", deny_unknown_fields)]
pub enum Command {
    Input { text: String },

    Backspace,

    Select { index: usize, revision: u64 },

    Space,

    Raw,

    Literal { text: String },

    Reset { private: bool },

    Flush,
}
