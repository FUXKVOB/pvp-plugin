use eframe::egui;
use std::sync::{Arc, Mutex};
use crate::server::ServerManager;
use crate::config::Config;
use crate::logs::LogViewer;

pub struct ManagerApp {
    server: Arc<Mutex<ServerManager>>,
    config: Config,
    logs: LogViewer,
    selected_tab: Tab,
    
    // Server control
    server_status: String,
    
    // Arena management
    arenas: Vec<String>,
    selected_arena: Option<usize>,
    new_arena_name: String,
    
    // Map management
    maps: Vec<String>,
    selected_map: Option<usize>,
    
    // Plugin settings
    max_players: String,
    enable_tournaments: bool,
    enable_cosmetics: bool,
}

#[derive(PartialEq)]
enum Tab {
    Dashboard,
    Server,
    Arenas,
    Maps,
    Logs,
    Settings,
}

impl ManagerApp {
    pub fn new(_cc: &eframe::CreationContext<'_>) -> Self {
        let server = Arc::new(Mutex::new(ServerManager::new()));
        let config = Config::load().unwrap_or_default();
        
        Self {
            server,
            config: config.clone(),
            logs: LogViewer::new(),
            selected_tab: Tab::Dashboard,
            server_status: "Остановлен".to_string(),
            arenas: vec![],
            selected_arena: None,
            new_arena_name: String::new(),
            maps: vec![],
            selected_map: None,
            max_players: "100".to_string(),
            enable_tournaments: true,
            enable_cosmetics: true,
        }
    }
}

impl eframe::App for ManagerApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        egui::TopBottomPanel::top("top_panel").show(ctx, |ui| {
            ui.horizontal(|ui| {
                ui.heading("🎮 PvPKits Server Manager");
                ui.separator();
                
                if ui.selectable_label(self.selected_tab == Tab::Dashboard, "📊 Панель").clicked() {
                    self.selected_tab = Tab::Dashboard;
                }
                if ui.selectable_label(self.selected_tab == Tab::Server, "🖥️ Сервер").clicked() {
                    self.selected_tab = Tab::Server;
                }
                if ui.selectable_label(self.selected_tab == Tab::Arenas, "⚔️ Арены").clicked() {
                    self.selected_tab = Tab::Arenas;
                }
                if ui.selectable_label(self.selected_tab == Tab::Maps, "🗺️ Карты").clicked() {
                    self.selected_tab = Tab::Maps;
                }
                if ui.selectable_label(self.selected_tab == Tab::Logs, "📝 Логи").clicked() {
                    self.selected_tab = Tab::Logs;
                }
                if ui.selectable_label(self.selected_tab == Tab::Settings, "⚙️ Настройки").clicked() {
                    self.selected_tab = Tab::Settings;
                }
            });
        });

        egui::CentralPanel::default().show(ctx, |ui| {
            match self.selected_tab {
                Tab::Dashboard => self.show_dashboard(ui),
                Tab::Server => self.show_server(ui),
                Tab::Arenas => self.show_arenas(ui),
                Tab::Maps => self.show_maps(ui),
                Tab::Logs => self.show_logs(ui),
                Tab::Settings => self.show_settings(ui),
            }
        });
    }
}

impl ManagerApp {
    fn show_dashboard(&mut self, ui: &mut egui::Ui) {
        ui.heading("Панель управления");
        ui.separator();
        
        ui.horizontal(|ui| {
            ui.group(|ui| {
                ui.vertical(|ui| {
                    ui.label("Статус сервера:");
                    ui.colored_label(
                        if self.server_status == "Запущен" { egui::Color32::GREEN } else { egui::Color32::RED },
                        &self.server_status
                    );
                });
            });
            
            ui.group(|ui| {
                ui.vertical(|ui| {
                    ui.label("Игроков онлайн:");
                    ui.heading("0");
                });
            });
            
            ui.group(|ui| {
                ui.vertical(|ui| {
                    ui.label("Активных арен:");
                    ui.heading(self.arenas.len().to_string());
                });
            });
        });
        
        ui.add_space(20.0);
        
        ui.group(|ui| {
            ui.heading("Быстрые действия");
            ui.horizontal(|ui| {
                if ui.button("🚀 Запустить сервер").clicked() {
                    self.start_server();
                }
                if ui.button("⏹️ Остановить сервер").clicked() {
                    self.stop_server();
                }
                if ui.button("🔄 Перезагрузить").clicked() {
                    self.restart_server();
                }
                if ui.button("📦 Собрать плагин").clicked() {
                    self.build_plugin();
                }
            });
        });
    }

    fn show_server(&mut self, ui: &mut egui::Ui) {
        ui.heading("Управление сервером");
        ui.separator();
        
        ui.group(|ui| {
            ui.label("Путь к серверу:");
            ui.text_edit_singleline(&mut self.config.server_path);
            
            ui.add_space(10.0);
            
            ui.horizontal(|ui| {
                if ui.button("🚀 Запустить").clicked() {
                    self.start_server();
                }
                if ui.button("⏹️ Остановить").clicked() {
                    self.stop_server();
                }
                if ui.button("🔄 Перезагрузить").clicked() {
                    self.restart_server();
                }
            });
        });
        
        ui.add_space(20.0);
        
        ui.group(|ui| {
            ui.heading("Сборка плагина");
            ui.label("Путь к проекту:");
            ui.text_edit_singleline(&mut self.config.plugin_path);
            
            ui.add_space(10.0);
            
            if ui.button("📦 Собрать и установить").clicked() {
                self.build_and_deploy();
            }
        });
    }

    fn show_arenas(&mut self, ui: &mut egui::Ui) {
        ui.heading("Управление аренами");
        ui.separator();
        
        ui.horizontal(|ui| {
            ui.label("Новая арена:");
            ui.text_edit_singleline(&mut self.new_arena_name);
            if ui.button("➕ Создать").clicked() && !self.new_arena_name.is_empty() {
                self.arenas.push(self.new_arena_name.clone());
                self.new_arena_name.clear();
            }
        });
        
        ui.add_space(10.0);
        
        let mut to_remove = None;
        
        egui::ScrollArea::vertical().show(ui, |ui| {
            for (idx, arena) in self.arenas.iter().enumerate() {
                ui.horizontal(|ui| {
                    if ui.selectable_label(self.selected_arena == Some(idx), arena).clicked() {
                        self.selected_arena = Some(idx);
                    }
                    if ui.button("🗑️").clicked() {
                        to_remove = Some(idx);
                    }
                });
            }
        });
        
        if let Some(idx) = to_remove {
            self.arenas.remove(idx);
            self.selected_arena = None;
        }
    }

    fn show_maps(&mut self, ui: &mut egui::Ui) {
        ui.heading("Управление картами");
        ui.separator();
        
        ui.label("Лобби и карты арен");
        
        egui::ScrollArea::vertical().show(ui, |ui| {
            ui.group(|ui| {
                ui.label("📍 Лобби");
                if ui.button("Установить текущую позицию").clicked() {
                    // Set lobby spawn
                }
            });
            
            ui.add_space(10.0);
            
            for (idx, map) in self.maps.iter().enumerate() {
                ui.group(|ui| {
                    ui.horizontal(|ui| {
                        ui.label(format!("🗺️ {}", map));
                        if ui.button("Редактировать").clicked() {
                            self.selected_map = Some(idx);
                        }
                    });
                });
            }
        });
    }

    fn show_logs(&mut self, ui: &mut egui::Ui) {
        ui.heading("Логи сервера");
        ui.separator();
        
        ui.horizontal(|ui| {
            if ui.button("🔄 Обновить").clicked() {
                self.logs.refresh();
            }
            if ui.button("🗑️ Очистить").clicked() {
                self.logs.clear();
            }
        });
        
        ui.add_space(10.0);
        
        egui::ScrollArea::vertical().show(ui, |ui| {
            for log in self.logs.get_logs() {
                ui.label(log);
            }
        });
    }

    fn show_settings(&mut self, ui: &mut egui::Ui) {
        ui.heading("Настройки плагина");
        ui.separator();
        
        ui.group(|ui| {
            ui.label("Максимум игроков:");
            ui.text_edit_singleline(&mut self.max_players);
            
            ui.add_space(10.0);
            
            ui.checkbox(&mut self.enable_tournaments, "Включить турниры");
            ui.checkbox(&mut self.enable_cosmetics, "Включить косметику");
        });
        
        ui.add_space(20.0);
        
        if ui.button("💾 Сохранить настройки").clicked() {
            self.save_config();
        }
    }

    fn start_server(&mut self) {
        if let Ok(mut server) = self.server.lock() {
            server.start(&self.config.server_path);
            self.server_status = "Запущен".to_string();
        }
    }

    fn stop_server(&mut self) {
        if let Ok(mut server) = self.server.lock() {
            server.stop();
            self.server_status = "Остановлен".to_string();
        }
    }

    fn restart_server(&mut self) {
        self.stop_server();
        std::thread::sleep(std::time::Duration::from_secs(2));
        self.start_server();
    }

    fn build_plugin(&mut self) {
        if let Ok(server) = self.server.lock() {
            server.build_plugin(&self.config.plugin_path);
        }
    }

    fn build_and_deploy(&mut self) {
        self.build_plugin();
        std::thread::sleep(std::time::Duration::from_secs(1));
        if let Ok(server) = self.server.lock() {
            server.deploy_plugin(&self.config.plugin_path, &self.config.server_path);
        }
    }

    fn save_config(&mut self) {
        self.config.save().ok();
    }
}
