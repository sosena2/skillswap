package rmi;

import config.DatabaseConfig;
import models.User;
import models.Blog;
import models.Category;
import models.Message;
import models.Report;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SkillSwapServiceImpl extends UnicastRemoteObject implements SkillSwapService {
    private static final long serialVersionUID = 1L;

    public SkillSwapServiceImpl() throws RemoteException {
        super();
    }

    private Connection getConnection() throws SQLException {
        try {
            Class.forName(DatabaseConfig.JDBC_DRIVER);
            return DriverManager.getConnection(DatabaseConfig.DB_URL,
                    DatabaseConfig.DB_USER,
                    DatabaseConfig.DB_PASSWORD);
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found", e);
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    @Override
    public User login(String username, String password) throws RemoteException {
        try (Connection conn = getConnection()) {
            String hashedPassword = hashPassword(password);
            String sql = "SELECT * FROM users WHERE (username = ? OR email = ?) AND password_hash = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            stmt.setString(2, username);
            stmt.setString(3, hashedPassword);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setUsername(rs.getString("username"));
                user.setEmail(rs.getString("email"));
                user.setFullName(rs.getString("full_name"));
                user.setBio(rs.getString("bio"));
                user.setProfilePicture(rs.getString("profile_picture"));
                user.setSocialLinks(rs.getString("social_links"));

                // Load skills
                loadUserSkills(user, conn);
                return user;
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error during login", e);
        }
        return null;
    }

    @Override
    public boolean register(User user) throws RemoteException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            // Insert user
            String sql = "INSERT INTO users (username, email, password_hash, full_name, bio, profile_picture, social_links) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPasswordHash());
            stmt.setString(4, user.getFullName());
            stmt.setString(5, user.getBio());
            stmt.setString(6, user.getProfilePicture());
            stmt.setString(7, user.getSocialLinks());

            int result = stmt.executeUpdate();
            if (result > 0) {
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    int userId = rs.getInt(1);
                    user.setId(userId);

                    // Insert skills
                    saveUserSkills(user, conn);
                    conn.commit();
                    return true;
                }
            }
            conn.rollback();
        } catch (SQLException e) {
            throw new RemoteException("Database error during registration", e);
        }
        return false;
    }

    @Override
    public boolean updateProfile(User user) throws RemoteException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            String sql = "UPDATE users SET full_name = ?, bio = ?, profile_picture = ?, social_links = ? WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, user.getFullName());
            stmt.setString(2, user.getBio());
            stmt.setString(3, user.getProfilePicture());
            stmt.setString(4, user.getSocialLinks());
            stmt.setInt(5, user.getId());

            int result = stmt.executeUpdate();
            if (result > 0) {
                // Delete existing skills
                deleteUserSkills(user.getId(), conn);
                // Insert updated skills
                saveUserSkills(user, conn);
                conn.commit();
                return true;
            }
            conn.rollback();
        } catch (SQLException e) {
            throw new RemoteException("Database error during profile update", e);
        }
        return false;
    }

    @Override
    public User getUserProfile(String username) throws RemoteException {
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM users WHERE username = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setUsername(rs.getString("username"));
                user.setEmail(rs.getString("email"));
                user.setFullName(rs.getString("full_name"));
                user.setBio(rs.getString("bio"));
                user.setProfilePicture(rs.getString("profile_picture"));
                user.setSocialLinks(rs.getString("social_links"));

                loadUserSkills(user, conn);
                return user;
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while fetching user profile", e);
        }
        return null;
    }

    @Override
    public List<String> getAllSkills() throws RemoteException {
        List<String> skills = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT skill_name FROM skills ORDER BY skill_name";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            while (rs.next()) {
                skills.add(rs.getString("skill_name"));
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while fetching skills", e);
        }
        return skills;
    }

    @Override
    public boolean addSkill(String skillName) throws RemoteException {
        try (Connection conn = getConnection()) {
            String sql = "INSERT IGNORE INTO skills (skill_name) VALUES (?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, skillName);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RemoteException("Database error while adding skill", e);
        }
    }

    @Override
    public List<User> searchUsersBySkills(List<String> skillsToLearn, String currentUsername) throws RemoteException {
        List<User> matchedUsers = new ArrayList<>();
        try (Connection conn = getConnection()) {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT DISTINCT u.* FROM users u ");
            sql.append("JOIN user_skills_teach ust ON u.id = ust.user_id ");
            sql.append("JOIN skills s ON ust.skill_id = s.id ");
            sql.append("WHERE u.username != ? ");

            if (!skillsToLearn.isEmpty()) {
                sql.append("AND s.skill_name IN (");
                for (int i = 0; i < skillsToLearn.size(); i++) {
                    sql.append("?");
                    if (i < skillsToLearn.size() - 1) sql.append(",");
                }
                sql.append(")");
            }

            PreparedStatement stmt = conn.prepareStatement(sql.toString());
            stmt.setString(1, currentUsername);

            for (int i = 0; i < skillsToLearn.size(); i++) {
                stmt.setString(i + 2, skillsToLearn.get(i));
            }

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setUsername(rs.getString("username"));
                user.setEmail(rs.getString("email"));
                user.setFullName(rs.getString("full_name"));
                user.setBio(rs.getString("bio"));
                user.setProfilePicture(rs.getString("profile_picture"));
                user.setSocialLinks(rs.getString("social_links"));

                loadUserSkills(user, conn);
                matchedUsers.add(user);
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error during user search", e);
        }
        return matchedUsers;
    }

    @Override
    public List<User> getAllUsers(String currentUsername) throws RemoteException {
        List<User> users = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM users WHERE username != ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, currentUsername);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setUsername(rs.getString("username"));
                user.setEmail(rs.getString("email"));
                user.setFullName(rs.getString("full_name"));
                user.setBio(rs.getString("bio"));
                user.setProfilePicture(rs.getString("profile_picture"));
                user.setSocialLinks(rs.getString("social_links"));

                loadUserSkills(user, conn);
                users.add(user);
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while fetching all users", e);
        }
        return users;
    }

    @Override
    public List<Message> getChatHistory(String user1, String user2) throws RemoteException {
        List<Message> messages = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM messages WHERE " +
                    "(sender_username = ? AND receiver_username = ?) OR " +
                    "(sender_username = ? AND receiver_username = ?) " +
                    "ORDER BY timestamp ASC";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, user1);
            stmt.setString(2, user2);
            stmt.setString(3, user2);
            stmt.setString(4, user1);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Message message = new Message();
                message.setId(rs.getInt("id"));
                message.setSenderUsername(rs.getString("sender_username"));
                message.setReceiverUsername(rs.getString("receiver_username"));
                message.setMessage(rs.getString("message"));
                message.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
                messages.add(message);
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while fetching chat history", e);
        }
        return messages;
    }

    @Override
    public void saveMessage(Message message) throws RemoteException {
        try (Connection conn = getConnection()) {
            String sql = "INSERT INTO messages (sender_username, receiver_username, message) VALUES (?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, message.getSenderUsername());
            stmt.setString(2, message.getReceiverUsername());
            stmt.setString(3, message.getMessage());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RemoteException("Database error while saving message", e);
        }
    }

    private void loadUserSkills(User user, Connection conn) throws SQLException {
        // Load skills to teach
        String sql = "SELECT s.skill_name FROM skills s " +
                "JOIN user_skills_teach ust ON s.id = ust.skill_id " +
                "WHERE ust.user_id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setInt(1, user.getId());
        ResultSet rs = stmt.executeQuery();

        List<String> skillsToTeach = new ArrayList<>();
        while (rs.next()) {
            skillsToTeach.add(rs.getString("skill_name"));
        }
        user.setSkillsToTeach(skillsToTeach);

        // Load skills to learn
        sql = "SELECT s.skill_name FROM skills s " +
                "JOIN user_skills_learn usl ON s.id = usl.skill_id " +
                "WHERE usl.user_id = ?";
        stmt = conn.prepareStatement(sql);
        stmt.setInt(1, user.getId());
        rs = stmt.executeQuery();

        List<String> skillsToLearn = new ArrayList<>();
        while (rs.next()) {
            skillsToLearn.add(rs.getString("skill_name"));
        }
        user.setSkillsToLearn(skillsToLearn);
    }

    private void saveUserSkills(User user, Connection conn) throws SQLException {
        // Save skills to teach
        for (String skill : user.getSkillsToTeach()) {
            int skillId = getOrCreateSkillId(skill, conn);
            String sql = "INSERT INTO user_skills_teach (user_id, skill_id) VALUES (?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, user.getId());
            stmt.setInt(2, skillId);
            stmt.executeUpdate();
        }

        // Save skills to learn
        for (String skill : user.getSkillsToLearn()) {
            int skillId = getOrCreateSkillId(skill, conn);
            String sql = "INSERT INTO user_skills_learn (user_id, skill_id) VALUES (?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, user.getId());
            stmt.setInt(2, skillId);
            stmt.executeUpdate();
        }
    }

    private void deleteUserSkills(int userId, Connection conn) throws SQLException {
        String sql = "DELETE FROM user_skills_teach WHERE user_id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setInt(1, userId);
        stmt.executeUpdate();

        sql = "DELETE FROM user_skills_learn WHERE user_id = ?";
        stmt = conn.prepareStatement(sql);
        stmt.setInt(1, userId);
        stmt.executeUpdate();
    }

    private int getOrCreateSkillId(String skillName, Connection conn) throws SQLException {
        // First try to get existing skill
        String sql = "SELECT id FROM skills WHERE skill_name = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, skillName);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            return rs.getInt("id");
        }

        // Create new skill if it doesn't exist
        sql = "INSERT INTO skills (skill_name) VALUES (?)";
        stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, skillName);
        stmt.executeUpdate();

        rs = stmt.getGeneratedKeys();
        if (rs.next()) {
            return rs.getInt(1);
        }

        throw new SQLException("Failed to create skill: " + skillName);
    }



    @Override
    public List<Message> getAllUserMessages(String username) throws RemoteException {
        List<Message> messages = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String sql = "SELECT * FROM messages WHERE sender_username = ? OR receiver_username = ? ORDER BY timestamp DESC";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            stmt.setString(2, username);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Message message = new Message();
                message.setId(rs.getInt("id"));
                message.setSenderUsername(rs.getString("sender_username"));
                message.setReceiverUsername(rs.getString("receiver_username"));
                message.setMessage(rs.getString("message"));
                message.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
                messages.add(message);
            }
        } catch (SQLException e) {
            throw new RemoteException("Database error while fetching all user messages", e);
        }
        return messages;
    }

    @Override
    public List<Blog> getAllBlogs() throws RemoteException {
        List<Blog> blogs = new ArrayList<>();
        String blogQuery = "SELECT b.*, u.username as author_name FROM Blog b JOIN users u ON b.user_id = u.id ORDER BY b.created_at DESC";
        String categoryQuery = "SELECT c.id, c.name FROM Category c JOIN BlogCategory bc ON c.id = bc.category_id WHERE bc.blog_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement blogStmt = conn.prepareStatement(blogQuery);
             ResultSet blogRs = blogStmt.executeQuery()) {

            while (blogRs.next()) {
                Blog blog = new Blog();
                blog.setId(blogRs.getInt("id"));
                blog.setUserId(blogRs.getInt("user_id"));
                blog.setAuthorName(blogRs.getString("author_name"));
                blog.setTitle(blogRs.getString("title"));
                blog.setContent(blogRs.getString("content"));
                blog.setCreatedAt(blogRs.getTimestamp("created_at"));
                blog.setUpdatedAt(blogRs.getTimestamp("updated_at"));

                // Get categories for this blog
                try (PreparedStatement categoryStmt = conn.prepareStatement(categoryQuery)) {
                    categoryStmt.setInt(1, blog.getId());
                    ResultSet categoryRs = categoryStmt.executeQuery();

                    List<Category> categories = new ArrayList<>();
                    while (categoryRs.next()) {
                        Category category = new Category();
                        category.setId(categoryRs.getInt("id"));
                        category.setName(categoryRs.getString("name"));
                        categories.add(category);
                    }
                    blog.setCategories(categories);
                }

                blogs.add(blog);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RemoteException("Failed to retrieve blogs: " + e.getMessage());
        }

        return blogs;
    }

    @Override
    public List<String> getOffenses() throws RemoteException {
        List<String> offenses = new ArrayList<>();
        String query = "SELECT name FROM OffenseTypes"; // Assuming you have an OffenseTypes table

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                offenses.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RemoteException("Failed to retrieve offense types: " + e.getMessage());
        }

        return offenses;
    }

    @Override
    public Boolean submitReport(Report report) throws RemoteException {
        String query = "INSERT INTO Reports (blog_id, author_name, offense_type, created_at) VALUES (?, ?, ?, NOW())";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, report.getBlogId());
            stmt.setString(2, report.getAuthorName());
            stmt.setString(3, report.getOffenseType());

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RemoteException("Failed to submit report: " + e.getMessage());
        }
    }
}
