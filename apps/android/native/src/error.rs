//! 桥接错误；JNI 边界转换成 Java 异常。
#[derive(Debug, thiserror::Error)]
pub enum AndroidError {
    #[error("invalid input: {0}")]
    Input(&'static str),

    #[error("engine handle does not belong to this thread")]
    Handle,

    #[error(transparent)]
    Dictionary(#[from] qingjian_dictionary::DictionaryError),

    #[error(transparent)]
    Glossary(#[from] qingjian_translate::GlossaryError),

    #[error(transparent)]
    Learning(#[from] qingjian_learning::LearningError),

    #[error(transparent)]
    Io(#[from] std::io::Error),

    #[error(transparent)]
    Json(#[from] serde_json::Error),

    #[error(transparent)]
    Jni(#[from] jni::errors::Error),
}
