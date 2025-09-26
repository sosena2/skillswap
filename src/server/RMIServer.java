package server;

import config.DatabaseConfig;
import rmi.SkillSwapService;
import rmi.SkillSwapServiceImpl;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RMIServer {
    public static void main(String[] args) {
        try {

            SkillSwapService service = new SkillSwapServiceImpl();

            Registry registry = LocateRegistry.createRegistry(DatabaseConfig.RMI_PORT);

            // Bind the service
            registry.rebind(DatabaseConfig.RMI_SERVICE_NAME, service);

            System.out.println("SkillSwap RMI Server started on port " + DatabaseConfig.RMI_PORT);
            System.out.println("Service bound as: " + DatabaseConfig.RMI_SERVICE_NAME);
            System.out.println("Server is ready to accept connections...");

            while (true) {
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            System.err.println("RMI Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
