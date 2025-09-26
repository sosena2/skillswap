package rmi;

import models.Blog;
import models.Message;
import models.Report;
import models.User;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface SkillSwapService extends Remote {
    // Authentication
    User login(String username, String password) throws RemoteException;
    boolean register(User user) throws RemoteException;

    // Profile Management
    boolean updateProfile(User user) throws RemoteException;
    User getUserProfile(String username) throws RemoteException;

    // Skills Management
    List<String> getAllSkills() throws RemoteException;
    boolean addSkill(String skillName) throws RemoteException;

    // User Search and Matching
    List<User> searchUsersBySkills(List<String> skillsToLearn, String currentUsername) throws RemoteException;
    List<User> getAllUsers(String currentUsername) throws RemoteException;

    // Chat History
    List<models.Message> getChatHistory(String user1, String user2) throws RemoteException;
    void saveMessage(models.Message message) throws RemoteException;



    List<Message> getAllUserMessages(String username) throws RemoteException;


    List<Blog> getAllBlogs() throws RemoteException;
    List<String> getOffenses() throws RemoteException;
    Boolean submitReport(Report report) throws RemoteException;
}
