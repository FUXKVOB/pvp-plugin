use std::process::{Child, Command, Stdio};
use std::path::Path;

pub struct ServerManager {
    process: Option<Child>,
}

impl ServerManager {
    pub fn new() -> Self {
        Self { process: None }
    }

    pub fn start(&mut self, server_path: &str) {
        if self.process.is_some() {
            println!("Сервер уже запущен");
            return;
        }

        let path = Path::new(server_path);
        if !path.exists() {
            println!("Путь к серверу не найден: {}", server_path);
            return;
        }

        match Command::new("java")
            .args(&[
                "-Xmx4G",
                "-Xms2G",
                "-jar",
                "paper.jar",
                "--nogui"
            ])
            .current_dir(path)
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
        {
            Ok(child) => {
                self.process = Some(child);
                println!("Сервер запущен");
            }
            Err(e) => println!("Ошибка запуска сервера: {}", e),
        }
    }

    pub fn stop(&mut self) {
        if let Some(mut process) = self.process.take() {
            process.kill().ok();
            println!("Сервер остановлен");
        }
    }

    pub fn build_plugin(&self, plugin_path: &str) {
        let path = Path::new(plugin_path);
        if !path.exists() {
            println!("Путь к плагину не найден: {}", plugin_path);
            return;
        }

        println!("Сборка плагина...");
        
        match Command::new("mvn")
            .args(&["clean", "package"])
            .current_dir(path)
            .output()
        {
            Ok(output) => {
                if output.status.success() {
                    println!("Плагин успешно собран");
                } else {
                    println!("Ошибка сборки: {}", String::from_utf8_lossy(&output.stderr));
                }
            }
            Err(e) => println!("Ошибка выполнения Maven: {}", e),
        }
    }

    pub fn deploy_plugin(&self, plugin_path: &str, server_path: &str) {
        let jar_path = format!("{}/target/PvPKits-1.0.0.jar", plugin_path);
        let dest_path = format!("{}/plugins/PvPKits-1.0.0.jar", server_path);

        match std::fs::copy(&jar_path, &dest_path) {
            Ok(_) => println!("Плагин установлен в {}", dest_path),
            Err(e) => println!("Ошибка копирования плагина: {}", e),
        }
    }

    pub fn is_running(&self) -> bool {
        self.process.is_some()
    }
}

impl Drop for ServerManager {
    fn drop(&mut self) {
        self.stop();
    }
}
