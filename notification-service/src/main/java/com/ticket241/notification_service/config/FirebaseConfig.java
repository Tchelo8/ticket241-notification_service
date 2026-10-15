package com.ticket241.notification_service.config;


import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${notification.fcm.enabled:false}")
    private boolean fcmEnabled;

    // Initialisation de Firebase Admin SDK au démarrage de l'application
    @PostConstruct
    public void initialize() {
        if (!fcmEnabled) {
            log.warn("Firebase Cloud Messaging est désactivé dans la configuration");
            log.info("Pour activer : notification.fcm.enabled=true dans application.yml");
            return;
        }

        try {
            // Vérifie si Firebase est déjà initialisé
            if (FirebaseApp.getApps().isEmpty()) {

                // Charge le fichier de configuration Firebase depuis classpath
                ClassPathResource resource = new ClassPathResource("firebase-service-account.json");

                if (!resource.exists()) {
                    log.error("Fichier firebase-service-account.json introuvable !");
                    log.error("Placez-le dans : src/main/resources/firebase-service-account.json");
                    log.warn("FCM ne sera pas disponible");
                    return;
                }

                // Ouvre le flux du fichier
                InputStream serviceAccount = resource.getInputStream();

                // Crée les options Firebase
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                // Initialise Firebase
                FirebaseApp.initializeApp(options);

                log.info("Firebase Admin SDK initialisé avec succès");
                log.info("Push notifications via FCM disponibles");

            } else {
                log.info("Firebase Admin SDK déjà initialisé");
            }

        } catch (IOException e) {
            log.error("Erreur lors de l'initialisation de Firebase");
            log.error("Message d'erreur : {}", e.getMessage());
            log.error("Vérifications :");
            log.error("   1. Le fichier firebase-service-account.json existe");
            log.error("   2. Le fichier est un JSON valide");
            log.error("   3. Le fichier contient les bonnes credentials");
            log.warn("L'application démarre mais FCM est indisponible");

            // En développement, je  peux continuer sans Firebase
            // En production, je devrais décommenter pour arrêter l'app si Firebase est critique
            // throw new RuntimeException("Firebase initialization failed", e);
        }
    }
}