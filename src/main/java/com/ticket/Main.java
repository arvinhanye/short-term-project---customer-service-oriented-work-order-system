package com.ticket;

import com.ticket.ui.MainFrame;
import com.ticket.service.CrossDatabaseRepairService;
import com.ticket.service.MongoLogRetryService;
import com.ticket.util.MongoDBUtil;
import com.ticket.util.MySQLDBUtil;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down ticket-management application");
            MongoDBUtil.close();
            MySQLDBUtil.close();
        }));

        try {
            MySQLDBUtil.getWriteDataSource();
            MySQLDBUtil.getReadDataSource();
            MongoDBUtil.getDatabase();
        } catch (Exception ex) {
            LOGGER.error("Failed to initialize database clients", ex);
            JOptionPane.showMessageDialog(null, "数据库初始化失败，请检查配置和数据库服务。", "启动失败", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            new MongoLogRetryService().retryPending();
            new CrossDatabaseRepairService().retryPending();
        } catch (Exception ex) {
            LOGGER.warn("Pending MongoDB writes could not be replayed; verify the incremental database script was executed", ex);
        }

        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame();
            mainFrame.setVisible(true);
        });
    }
}
