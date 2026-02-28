use eframe::egui;
use std::sync::Arc;

mod server;
mod ui;
mod config;
mod logs;

use ui::ManagerApp;

fn main() -> Result<(), eframe::Error> {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1200.0, 800.0])
            .with_min_inner_size([800.0, 600.0])
            .with_icon(load_icon()),
        ..Default::default()
    };

    eframe::run_native(
        "PvPKits Server Manager v1.0",
        options,
        Box::new(|cc| {
            egui_extras::install_image_loaders(&cc.egui_ctx);
            Ok(Box::new(ManagerApp::new(cc)))
        }),
    )
}

fn load_icon() -> Arc<egui::IconData> {
    let (icon_rgba, icon_width, icon_height) = {
        let icon = include_bytes!("../assets/icon.png");
        let image = image::load_from_memory(icon)
            .expect("Failed to load icon")
            .into_rgba8();
        let (width, height) = image.dimensions();
        let rgba = image.into_raw();
        (rgba, width, height)
    };

    Arc::new(egui::IconData {
        rgba: icon_rgba,
        width: icon_width,
        height: icon_height,
    })
}
