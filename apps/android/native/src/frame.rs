//! 返回给键盘的显示帧；不暴露排序或词库实现。
use serde::Serialize;

#[derive(Debug, Serialize)]
pub struct Frame {
    pub revision: u64,

    pub preedit: String,

    pub committed: String,

    pub delete_backward: bool,

    pub candidates: Vec<DisplayCandidate>,
}

#[derive(Debug, Serialize)]
pub struct DisplayCandidate {
    pub text: String,

    pub translation: String,
}
