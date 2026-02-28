use std::collections::VecDeque;

pub struct LogViewer {
    logs: VecDeque<String>,
    max_logs: usize,
}

impl LogViewer {
    pub fn new() -> Self {
        Self {
            logs: VecDeque::new(),
            max_logs: 1000,
        }
    }

    pub fn add_log(&mut self, log: String) {
        if self.logs.len() >= self.max_logs {
            self.logs.pop_front();
        }
        self.logs.push_back(log);
    }

    pub fn get_logs(&self) -> Vec<String> {
        self.logs.iter().cloned().collect()
    }

    pub fn clear(&mut self) {
        self.logs.clear();
    }

    pub fn refresh(&mut self) {
        // TODO: Read from server log file
    }
}
