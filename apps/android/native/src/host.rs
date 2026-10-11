//! JNI 生命周期；线程局部注册表避免将 Java 的整数句柄当成裸指针。
use std::cell::RefCell;
use std::collections::HashMap;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::path::Path;
use std::sync::atomic::{AtomicI64, Ordering};

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jlong, jstring};

use crate::command::Command;
use crate::error::AndroidError;
use crate::session::Session;

thread_local! {
    static SESSIONS: RefCell<HashMap<i64, Session>> = RefCell::new(HashMap::new());
}
static NEXT_HANDLE: AtomicI64 = AtomicI64::new(1);

fn boundary<T: Default>(
    env: &mut JNIEnv<'_>,
    f: impl FnOnce(&mut JNIEnv<'_>) -> Result<T, AndroidError>,
) -> T {
    match catch_unwind(AssertUnwindSafe(|| f(env))) {
        Ok(Ok(value)) => value,
        Ok(Err(error)) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.to_string());
            T::default()
        }
        Err(_) => {
            let _ = env.throw_new("java/lang/IllegalStateException", "native engine failed");
            T::default()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_app_qingjian_android_NativeEngine_create(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    resources: JString<'_>,
    user: JString<'_>,
) -> jlong {
    boundary(&mut env, |env| {
        let resources: String = env.get_string(&resources)?.into();
        let user: String = env.get_string(&user)?.into();
        let session = Session::open(Path::new(&resources), Path::new(&user))?;
        let handle = NEXT_HANDLE
            .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |n| n.checked_add(1))
            .map_err(|_| AndroidError::Input("handle overflow"))?;
        SESSIONS.with(|sessions| sessions.borrow_mut().insert(handle, session));
        Ok(handle)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_app_qingjian_android_NativeEngine_dispatch(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    json: JString<'_>,
) -> jstring {
    boundary(&mut env, |env| {
        let json: String = env.get_string(&json)?.into();
        let command: Command = serde_json::from_str(&json)?;
        let frame = SESSIONS.with(|sessions| {
            sessions
                .borrow_mut()
                .get_mut(&handle)
                .ok_or(AndroidError::Handle)?
                .dispatch(command)
        })?;
        Ok(env.new_string(serde_json::to_string(&frame)?)?.into_raw())
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_app_qingjian_android_NativeEngine_destroy(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    boundary(&mut env, |_| {
        SESSIONS.with(|sessions| sessions.borrow_mut().remove(&handle));
        Ok(())
    });
}
