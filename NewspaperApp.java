import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Vector;

public class NewspaperApp extends JFrame {

    private final NewsData model;
    private JTable newsTable;
    private DefaultTableModel tableModel;
    private User currentUser = null;
    private JButton btnLoginLogout;
    private JButton btnAddNews;

    public class User {
        private int id;
        private String username;

        public User(int id, String username) {
            this.id = id;
            this.username = username;
        }

        public int getId() { return id; }
        public String getUsername() { return username; }
    }

    public NewspaperApp() {
        this.model = new NewsData();
        setTitle("News Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setLayout(new BorderLayout(15, 15));
        setSize(1100, 700);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {

        }

        if (!model.testConnection()) {
            JOptionPane.showMessageDialog(null, "Database Connection Failed! Check your MySQL server, driver, and credentials (newspaper_db, root, wwe@75).", "Connection Error", JOptionPane.ERROR_MESSAGE);
        }

        initializeUI();
        loadNewsData();

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initializeUI() {
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 30, 15));

        JButton btnManageUsers = new JButton("Manage Users (Admin)");
        btnAddNews = new JButton("Add News Article");
        btnLoginLogout = new JButton("Login");

        Font buttonFont = new Font("Arial", Font.BOLD, 15);

        btnManageUsers.setFont(buttonFont);
        btnAddNews.setFont(buttonFont);
        btnLoginLogout.setFont(buttonFont);

        btnManageUsers.setBackground(new Color(240, 240, 240));
        btnAddNews.setBackground(new Color(150, 200, 255));

        controlPanel.add(btnManageUsers);
        controlPanel.add(btnAddNews);
        controlPanel.add(btnLoginLogout);

        add(controlPanel, BorderLayout.NORTH);

        Vector<String> columnNames = new Vector<>();
        columnNames.add("News_id");
        columnNames.add("Title");
        columnNames.add("Created_at");
        columnNames.add("Author_Name");
        columnNames.add("Show Body");
        columnNames.add("Modify");
        columnNames.add("Delete");

        tableModel = new DefaultTableModel(new Vector<>(), columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column >= 4;
            }
        };

        newsTable = new JTable(tableModel);
        newsTable.setRowHeight(35);
        newsTable.setAutoCreateRowSorter(true);
        newsTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 16));
        newsTable.setFont(new Font("Arial", Font.PLAIN, 14));

        JScrollPane scrollPane = new JScrollPane(newsTable);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 15, 15, 15),
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY), "All News Articles", 0, 0, new Font("Arial", Font.BOLD, 18))
        ));
        add(scrollPane, BorderLayout.CENTER);

        btnManageUsers.addActionListener(e -> new UserManagementDialog(this, model).setVisible(true));

        btnAddNews.addActionListener(e -> {
            if (currentUser != null) {
                showAddNewsDialog();
            } else {
                JOptionPane.showMessageDialog(this, "Please log in to add news.", "Access Denied", JOptionPane.WARNING_MESSAGE);
            }
        });

        btnLoginLogout.addActionListener(e -> {
            if (currentUser == null) {
                showLoginWindow();
            } else {
                logout();
            }
        });

        setupTableButtonColumns();
        updateUIForLoginState();
    }

    private void setupTableButtonColumns() {
        if (newsTable.getColumnModel().getColumnCount() < 7) return;

        TableColumnModel columnModel = newsTable.getColumnModel();

        columnModel.getColumn(4).setCellRenderer(new ButtonRenderer("View"));
        columnModel.getColumn(4).setCellEditor(new ButtonEditor(new JCheckBox(), newsTable, this, "View"));
        columnModel.getColumn(4).setMaxWidth(80);

        columnModel.getColumn(5).setCellRenderer(new ButtonRenderer("Edit"));
        columnModel.getColumn(5).setCellEditor(new ButtonEditor(new JCheckBox(), newsTable, this, "Edit"));
        columnModel.getColumn(5).setMaxWidth(80);

        columnModel.getColumn(6).setCellRenderer(new ButtonRenderer("Delete"));
        columnModel.getColumn(6).setCellEditor(new ButtonEditor(new JCheckBox(), newsTable, this, "Delete"));
        columnModel.getColumn(6).setMaxWidth(80);

        columnModel.getColumn(0).setMaxWidth(60);
    }

    public void loadNewsData() {
        try (ResultSet rs = model.getAllNews()) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            Vector<String> columnNames = new Vector<>();
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(metaData.getColumnName(i));
            }
            columnNames.add("Show Body");
            columnNames.add("Modify");
            columnNames.add("Delete");

            tableModel.setColumnIdentifiers(columnNames);
            tableModel.setRowCount(0);

            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.add(rs.getObject(i));
                }
                row.add("View");
                row.add("Edit");
                row.add("Delete");
                tableModel.addRow(row);
            }
            setupTableButtonColumns();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Failed to load news data. Check if 'news' table exists: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showLoginWindow() {
        JTextField usernameField = new JTextField(15);
        JPasswordField passwordField = new JPasswordField(15);

        JPanel loginPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        loginPanel.add(new JLabel("Username (Name):"));
        loginPanel.add(usernameField);
        loginPanel.add(new JLabel("Password:"));
        loginPanel.add(passwordField);

        int result = JOptionPane.showConfirmDialog(this, loginPanel, "Login", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            User user = model.authenticateUser(username, password, this);

            if (user != null) {
                currentUser = user;
                JOptionPane.showMessageDialog(this, "Login successful. Welcome, " + user.getUsername() + "!");
                updateUIForLoginState();
            } else {
                JOptionPane.showMessageDialog(this, "Invalid username or password.", "Login Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void logout() {
        currentUser = null;
        JOptionPane.showMessageDialog(this, "Logged out successfully.");
        updateUIForLoginState();
    }

    private void updateUIForLoginState() {
        if (currentUser != null) {
            btnLoginLogout.setText("Logout (" + currentUser.getUsername() + ")");
            btnAddNews.setEnabled(true);
        } else {
            btnLoginLogout.setText("Login");
            btnAddNews.setEnabled(false);
        }
        newsTable.repaint();
    }

    public void showNewsDetails(int newsId, String title) {
        String body = model.getNewsBody(newsId);

        JTextArea textArea = new JTextArea(body);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
        textArea.setEditable(false);
        textArea.setFont(new Font("Arial", Font.PLAIN, 15));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(650, 450));

        JOptionPane.showMessageDialog(this, scrollPane, "Viewing: " + title, JOptionPane.PLAIN_MESSAGE);
    }

    private void showAddNewsDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 5, 10, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField titleField = new JTextField(35);
        JTextArea bodyArea = new JTextArea(10, 35);
        bodyArea.setLineWrap(true);
        bodyArea.setFont(new Font("Arial", Font.PLAIN, 14));

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Title:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; panel.add(titleField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.NORTHWEST; panel.add(new JLabel("Content:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.gridheight = 2; gbc.weighty = 1.0; panel.add(new JScrollPane(bodyArea), gbc);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add New News Article (Author: " + currentUser.getUsername() + ")", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String title = titleField.getText().trim();
            String body = bodyArea.getText().trim();
            int uId = currentUser.getId();

            if (title.isEmpty() || body.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Title and Body are required.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (model.addNews(title, body, uId)) {
                JOptionPane.showMessageDialog(this, "News added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                loadNewsData();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to add news. Check the console for detailed SQL error message.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void handleModifyNews(int newsId, String currentTitle) {

        String currentBody = model.getNewsBody(newsId);

        JTextField titleField = new JTextField(currentTitle, 30);
        JTextArea bodyArea = new JTextArea(currentBody, 10, 30);
        bodyArea.setLineWrap(true);
        bodyArea.setFont(new Font("Arial", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(bodyArea);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 5, 10, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Title:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; panel.add(titleField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.NORTHWEST; panel.add(new JLabel("Content:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.gridheight = 2; gbc.weighty = 1.0; panel.add(scrollPane, gbc);

        int result = JOptionPane.showConfirmDialog(this, panel, "Modify News Article (ID: " + newsId + ")", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String newTitle = titleField.getText().trim();
            String newBody = bodyArea.getText().trim();

            if (newTitle.isEmpty() || newBody.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Title and Body are required.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (model.modifyNews(newsId, newTitle, newBody)) {
                JOptionPane.showMessageDialog(this, "News modified successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                loadNewsData();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to modify news.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void handleDeleteNews(int newsId) {

        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete News ID " + newsId + "? This action cannot be undone.", "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            if (model.deleteNews(newsId)) {
                JOptionPane.showMessageDialog(this, "News article ID " + newsId + " deleted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                loadNewsData();
            } else {
                JOptionPane.showMessageDialog(this, "Failed to delete news.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new NewspaperApp());
    }

    private static class UserManagementDialog extends JDialog {
        private final NewspaperApp parentApp;
        private final NewsData model;
        private JTable userTable;
        private DefaultTableModel userTableModel;

        public UserManagementDialog(NewspaperApp parent, NewsData model) {
            super(parent, "User Management List", true);
            this.parentApp = parent;
            this.model = model;
            setSize(700, 450);
            setLayout(new BorderLayout(15, 15));
            setLocationRelativeTo(parent);

            initializeUserUI();
            loadUserData();
        }

        private void initializeUserUI() {
            userTableModel = new DefaultTableModel();
            userTable = new JTable(userTableModel);
            userTable.setRowHeight(30);
            userTable.setAutoCreateRowSorter(true);
            userTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 15));
            userTable.setFont(new Font("Arial", Font.PLAIN, 14));

            JScrollPane scrollPane = new JScrollPane(userTable);
            scrollPane.setBorder(BorderFactory.createTitledBorder("All Registered Users"));
            add(scrollPane, BorderLayout.CENTER);

            JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 10));
            JButton btnAddUser = new JButton("Add New User");
            JButton btnDeleteUser = new JButton("Delete Selected User");

            Font smallFont = new Font("Arial", Font.BOLD, 14);
            btnAddUser.setFont(smallFont);
            btnDeleteUser.setFont(smallFont);

            btnAddUser.addActionListener(e -> {
                showAddUserDialog();
                loadUserData();
            });

            btnDeleteUser.addActionListener(e -> handleDeleteUser());

            controlPanel.add(btnAddUser);
            controlPanel.add(btnDeleteUser);
            add(controlPanel, BorderLayout.SOUTH);
        }

        private void loadUserData() {
            try (ResultSet rs = model.getAllUsers()) {
                userTableModel.setRowCount(0);

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                Vector<String> columnNames = new Vector<>();
                for (int i = 1; i <= columnCount; i++) {
                    columnNames.add(metaData.getColumnName(i));
                }
                userTableModel.setColumnIdentifiers(columnNames);

                while (rs.next()) {
                    Vector<Object> row = new Vector<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.add(rs.getObject(i));
                    }
                    userTableModel.addRow(row);
                }
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, "Failed to load user data: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void showAddUserDialog() {
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JTextField nameField = new JTextField(20);
            JTextField emailField = new JTextField(20);
            JTextField ageField = new JTextField(5);
            JPasswordField passwordField = new JPasswordField(20);

            gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Name (Username):"), gbc);
            gbc.gridx = 1; gbc.gridy = 0; panel.add(nameField, gbc);

            gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Email (Optional):"), gbc);
            gbc.gridx = 1; gbc.gridy = 1; panel.add(emailField, gbc);

            gbc.gridx = 0; gbc.gridy = 2; panel.add(new JLabel("Password:"), gbc);
            gbc.gridx = 1; gbc.gridy = 2; panel.add(passwordField, gbc);

            gbc.gridx = 0; gbc.gridy = 3; panel.add(new JLabel("Age (Optional):"), gbc);
            gbc.gridx = 1; gbc.gridy = 3; panel.add(ageField, gbc);


            int result = JOptionPane.showConfirmDialog(this, panel, "Add New User", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                String name = nameField.getText().trim();
                String email = emailField.getText().trim();
                String password = new String(passwordField.getPassword());
                int age = 0;

                try {
                    String ageText = ageField.getText().trim();
                    if (!ageText.isEmpty()) {
                        age = Integer.parseInt(ageText);
                    }
                } catch (NumberFormatException ignored) {}

                if (name.isEmpty() || password.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Name and Password are required.", "Input Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (model.addUser(name, email, age, password)) {
                    JOptionPane.showMessageDialog(this, "User added successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to add user. Check if Name/Email is already used.", "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        private void handleDeleteUser() {
            int selectedRow = userTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a user to delete.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int selectedUserId = (int) userTableModel.getValueAt(userTable.convertRowIndexToModel(selectedRow), 0);
            String name = (String) userTableModel.getValueAt(userTable.convertRowIndexToModel(selectedRow), 1);

            if (parentApp.currentUser == null || parentApp.currentUser.getId() != selectedUserId) {
                JOptionPane.showMessageDialog(this, "Access Denied. You can only delete your own account.", "Security Warning", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete user ID " + selectedUserId + " (" + name + ")? This action cannot be undone.", "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (confirm == JOptionPane.YES_OPTION) {
                if (model.deleteUser(selectedUserId)) {
                    JOptionPane.showMessageDialog(this, "User ID " + selectedUserId + " deleted successfully! You have been logged out.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    parentApp.logout();
                    loadUserData();
                    parentApp.loadNewsData();
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to delete user. (Possible: User has existing news articles linked by Foreign Key)", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private static class NewsData {

        private static final String URL = "jdbc:mysql://localhost:3306/newspaper_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
        private static final String USER = "root";
        private static final String PASSWORD = "wwe@75";

        public Connection getConnection() throws SQLException {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                System.err.println("FATAL ERROR: MySQL JDBC Driver not found.");
                throw new SQLException("MySQL Driver not found.", e);
            }
            return DriverManager.getConnection(URL, USER, PASSWORD);
        }

        public boolean testConnection() {
            try (Connection conn = getConnection()) {
                createTablesIfNotExist(conn);
                return conn != null;
            } catch (SQLException e) {
                System.err.println("Connection Test Failed: " + e.getMessage());
                return false;
            }
        }

        private void createTablesIfNotExist(Connection conn) throws SQLException {
            String createUserTable = "CREATE TABLE IF NOT EXISTS user ("
                    + "U_id INT PRIMARY KEY AUTO_INCREMENT,"
                    + "Name VARCHAR(100) NOT NULL UNIQUE,"
                    + "Email VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL UNIQUE,"
                    + "Age INT NULL,"
                    + "Password VARCHAR(255) NOT NULL"
                    + ") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;";

            String createNewsTable = "CREATE TABLE IF NOT EXISTS news ("
                    + "News_id INT PRIMARY KEY AUTO_INCREMENT,"
                    + "Title VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
                    + "Body TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,"
                    + "Created_at DATETIME NOT NULL,"
                    + "U_id INT NOT NULL,"
                    + "FOREIGN KEY (U_id) REFERENCES user(U_id)"
                    + ") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;";

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createUserTable);
                stmt.execute(createNewsTable);
            }
        }

        public User authenticateUser(String username, String password, NewspaperApp app) {
            String sql = "SELECT U_id, Name FROM user WHERE Name = ? AND Password = ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, username);
                pstmt.setString(2, password);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return app.new User(rs.getInt("U_id"), rs.getString("Name"));
                    }
                }
            } catch (SQLException e) {
                System.err.println("Database Error (Authenticate): " + e.getMessage());
            }
            return null;
        }

        public boolean addUser(String name, String email, int age, String password) {
            String sql = "INSERT INTO user (Name, Email, Age, Password) VALUES (?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, name);
                pstmt.setString(2, email.isEmpty() ? null : email);
                if (age > 0) {
                    pstmt.setInt(3, age);
                } else {
                    pstmt.setNull(3, Types.INTEGER);
                }
                pstmt.setString(4, password);

                return pstmt.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("Database Error (Add User): " + e.getMessage());
                return false;
            }
        }

        public ResultSet getAllUsers() throws SQLException {
            Connection conn = getConnection();
            String sql = "SELECT U_id, Name, Email, Age FROM user ORDER BY U_id ASC";

            Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            return stmt.executeQuery(sql);
        }

        public boolean deleteUser(int uId) {
            String sql = "DELETE FROM user WHERE U_id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, uId);
                return pstmt.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("Database Error (Delete User): " + e.getMessage());
                return false;
            }
        }

        public boolean addNews(String title, String body, int uId) {
            String sql = "INSERT INTO news (Title, Body, Created_at, U_id) VALUES (?, ?, ?, ?)";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, title);
                pstmt.setString(2, body);
                pstmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                pstmt.setInt(4, uId);

                return pstmt.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("ERROR: Failed to add news. Detailed SQL error:");
                System.err.println("SQL State: " + e.getSQLState());
                System.err.println("Error Code: " + e.getErrorCode());
                System.err.println("Message: " + e.getMessage());
                return false;
            }
        }

        public ResultSet getAllNews() throws SQLException {
            Connection conn = getConnection();
            String sql = "SELECT N.News_id, N.Title, N.Created_at, U.Name AS Author_Name " +
                    "FROM news N JOIN user U ON N.U_id = U.U_id " +
                    "ORDER BY N.Created_at DESC";

            Statement stmt = conn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            return stmt.executeQuery(sql);
        }

        public String getNewsBody(int newsId) {
            String sql = "SELECT Body FROM news WHERE News_id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, newsId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("Body");
                    }
                }
            } catch (SQLException e) {
                System.err.println("Database Error (Get News Body): " + e.getMessage());
            }
            return "Error: Could not retrieve news body.";
        }

        public boolean modifyNews(int newsId, String newTitle, String newBody) {
            String sql = "UPDATE news SET Title = ?, Body = ? WHERE News_id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, newTitle);
                pstmt.setString(2, newBody);
                pstmt.setInt(3, newsId);

                return pstmt.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("Database Error (Modify News): " + e.getMessage());
                return false;
            }
        }

        public boolean deleteNews(int newsId) {
            String sql = "DELETE FROM news WHERE News_id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, newsId);
                return pstmt.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("Database Error (Delete News): " + e.getMessage());
                return false;
            }
        }
    }

    private class ButtonRenderer extends JButton implements TableCellRenderer {
        private String text;
        public ButtonRenderer(String text) {
            setOpaque(true);
            this.text = text;
            setFocusPainted(false);
            setBackground(new Color(220, 220, 220));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {

            setText(text);

            if (column >= 5) {
                int modelRow = table.convertRowIndexToModel(row);

                if (currentUser == null) {
                    setEnabled(false);
                } else if (modelRow != -1) {
                    String authorName = (String) tableModel.getValueAt(modelRow, 3);

                    if (!currentUser.getUsername().equals(authorName)) {
                        setEnabled(false);
                    } else {
                        setEnabled(true);
                    }
                } else {
                    setEnabled(false);
                }
            } else {
                setEnabled(true);
            }

            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(UIManager.getColor("Button.background"));
            }
            return this;
        }
    }

    private class ButtonEditor extends DefaultCellEditor {
        private JButton button;
        private String label;
        private boolean isPushed;
        private JTable table;
        private NewspaperApp app;
        private String actionType;

        public ButtonEditor(JCheckBox checkBox, JTable table, NewspaperApp app, String actionType) {
            super(checkBox);
            this.table = table;
            this.app = app;
            this.actionType = actionType;

            button = new JButton();
            button.setOpaque(true);
            button.setBackground(new Color(220, 220, 220));

            button.addActionListener(e -> {
                isPushed = true;
                fireEditingStopped();
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            label = (value == null) ? "" : value.toString();
            button.setText(label);

            if (column >= 5) {
                int modelRow = table.convertRowIndexToModel(row);
                if (app.currentUser == null) {
                    button.setEnabled(false);
                } else if (modelRow != -1) {
                    String authorName = (String) tableModel.getValueAt(modelRow, 3);
                    if (!app.currentUser.getUsername().equals(authorName)) {
                        button.setEnabled(false);
                    } else {
                        button.setEnabled(true);
                    }
                } else {
                    button.setEnabled(false);
                }
            } else {
                button.setEnabled(true);
            }

            isPushed = true;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            if (isPushed) {
                int selectedRow = table.convertRowIndexToModel(table.getEditingRow());
                if (selectedRow != -1) {
                    int newsId = (int) tableModel.getValueAt(selectedRow, 0);
                    String title = (String) tableModel.getValueAt(selectedRow, 1);
                    String authorName = (String) tableModel.getValueAt(selectedRow, 3);

                    if (actionType.equals("View")) {
                        app.showNewsDetails(newsId, title);
                    } else if (actionType.equals("Edit") || actionType.equals("Delete")) {

                        if (app.currentUser != null && app.currentUser.getUsername().equals(authorName)) {
                            if (actionType.equals("Edit")) {
                                app.handleModifyNews(newsId, title);
                            } else if (actionType.equals("Delete")) {
                                app.handleDeleteNews(newsId);
                            }
                        } else {
                            if (app.currentUser != null) {
                                JOptionPane.showMessageDialog(app, "You can only Edit/Delete your own articles! Author: " + authorName, "Access Denied", JOptionPane.ERROR_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(app, "Please log in to perform this action.", "Access Denied", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }
                }
            }
            isPushed = false;
            return label;
        }

        @Override
        public boolean stopCellEditing() {
            isPushed = false;
            return super.stopCellEditing();
        }
    }
}