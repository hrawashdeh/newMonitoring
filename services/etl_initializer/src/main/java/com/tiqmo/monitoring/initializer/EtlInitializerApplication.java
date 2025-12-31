package com.tiqmo.monitoring.initializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.tiqmo.monitoring.initializer.config.AuthData;
import com.tiqmo.monitoring.initializer.config.LoaderData;
import com.tiqmo.monitoring.initializer.config.MessageData;
import com.tiqmo.monitoring.initializer.domain.Loader;
import com.tiqmo.monitoring.initializer.domain.SourceDatabase;
import com.tiqmo.monitoring.initializer.domain.enums.DbType;
import com.tiqmo.monitoring.initializer.domain.enums.LoadStatus;
import com.tiqmo.monitoring.initializer.domain.enums.PurgeStrategy;
import com.tiqmo.monitoring.initializer.repository.LoaderRepository;
import com.tiqmo.monitoring.initializer.repository.SourceDatabaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

@Slf4j
@SpringBootApplication
@EnableScheduling
public class EtlInitializerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EtlInitializerApplication.class, args);
        log.info("=== ETL Initializer Service Started ===");
        log.info("=== Monitoring for new YAML files... ===");
    }
}

@Slf4j
@Component
@RequiredArgsConstructor
class FileMonitorService {

    private final SourceDatabaseRepository sourceDbRepo;
    private final LoaderRepository loaderRepo;
    private final JdbcTemplate jdbcTemplate;
    private final com.tiqmo.monitoring.initializer.infra.security.EncryptionService encryptionService;

    @Value("${etl.file.upload-path:/data/uploads}")
    private String uploadPath;

    @Value("${etl.file.processed-path:/data/processed}")
    private String processedPath;

    @Value("${etl.file.failed-path:/data/failed}")
    private String failedPath;

    private static final DateTimeFormatter CSV_TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"));

    @Scheduled(fixedDelayString = "${etl.file.scan-interval-seconds:60}000", initialDelay = 10000)
    public void scanForNewFiles() {
        log.debug("Scanning {} for new YAML files...", uploadPath);

        File uploadDir = new File(uploadPath);
        if (!uploadDir.exists()) {
            log.warn("Upload directory does not exist: {}", uploadPath);
            return;
        }

        File[] yamlFiles = uploadDir.listFiles((dir, name) ->
            name.toLowerCase().endsWith(".yaml") || name.toLowerCase().endsWith(".yml"));

        if (yamlFiles == null || yamlFiles.length == 0) {
            log.trace("No new files found in {}", uploadPath);
            return;
        }

        log.info("Found {} YAML file(s) to process", yamlFiles.length);

        for (File file : yamlFiles) {
            try {
                String fileName = file.getName();
                if (fileName.startsWith("etl-data")) {
                    log.info("Detected ETL data file: {}", fileName);
                    processEtlFile(file);
                } else if (fileName.startsWith("auth-data")) {
                    log.info("Detected Auth data file: {}", fileName);
                    processAuthFile(file);
                } else if (fileName.startsWith("messages-data")) {
                    log.info("Detected Messages data file: {}", fileName);
                    processMessagesFile(file);
                } else {
                    log.warn("Unknown file type: {}. Skipping.", fileName);
                }
            } catch (Exception e) {
                log.error("Error processing file {}: {}", file.getName(), e.getMessage(), e);
                moveToFailed(file, e.getMessage());
            }
        }
    }

    private void processEtlFile(File file) throws Exception {
        String fileName = file.getName();
        String filePath = file.getAbsolutePath();
        long fileSize = file.length();

        log.info("Processing file: {} ({} bytes)", fileName, fileSize);

        // Read YAML content for encryption
        String yamlContent = Files.readString(file.toPath());
        log.debug("Read YAML content: {} characters", yamlContent.length());

        // Encrypt YAML content using AES-256-GCM
        String encryptedContent = encryptionService.encrypt(yamlContent);
        log.debug("Encrypted YAML content: {} characters", encryptedContent.length());

        // Calculate SHA-256 hash
        String fileHash = calculateFileHash(file);
        log.debug("File hash: {}", fileHash);

        // Check if already processed
        if (isAlreadyProcessed(fileHash)) {
            log.warn("File {} already processed (duplicate hash). Skipping.", fileName);
            moveToProcessed(file);
            return;
        }

        // Parse YAML file
        LoaderData data = parseEtlYamlFile(file);

        if (data.getMetadata() == null || data.getMetadata().getLoadVersion() == null) {
            throw new IllegalArgumentException("Missing metadata.load_version in YAML file");
        }

        int targetVersion = data.getMetadata().getLoadVersion();
        int currentVersion = getCurrentVersion();

        log.info("File version: {}, Current system version: {}", targetVersion, currentVersion);

        if (targetVersion <= currentVersion) {
            log.warn("File version {} is not greater than current version {}. Skipping.",
                targetVersion, currentVersion);
            moveToProcessed(file);
            return;
        }

        // Mark as processing (store encrypted YAML content)
        long logId = createInitializationLog(fileName, filePath, fileSize, fileHash,
            targetVersion, encryptedContent, "PROCESSING");

        try {
            // Load source databases
            int sourcesLoaded = loadSourceDatabases(data, currentVersion);
            log.info("Loaded {} new source databases", sourcesLoaded);

            // Load loaders
            int loadersLoaded = loadLoaders(data, currentVersion);
            log.info("Loaded {} new loaders", loadersLoaded);

            // Generate CSV metadata
            String csvMetadata = generateCsvMetadata(fileName, targetVersion, sourcesLoaded, loadersLoaded);

            // Update initialization log
            updateInitializationLog(logId, sourcesLoaded, loadersLoaded, csvMetadata, "COMPLETED", null);

            // Update system version
            updateCurrentVersion(targetVersion);

            // Mark system as initialized if first run
            if (currentVersion == 0) {
                markSystemInitialized();
            }

            log.info("Successfully processed file: {} (version {})", fileName, targetVersion);

            // Move to processed
            moveToProcessed(file);

        } catch (Exception e) {
            updateInitializationLog(logId, 0, 0, null, "FAILED", e.getMessage());
            throw e;
        }
    }

    private String calculateFileHash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = Files.readAllBytes(file.toPath());
        byte[] hashBytes = digest.digest(fileBytes);
        return HexFormat.of().formatHex(hashBytes);
    }

    private boolean isAlreadyProcessed(String fileHash) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM general.initialization_log WHERE file_hash = ?",
            Integer.class,
            fileHash
        );
        return count != null && count > 0;
    }

    private LoaderData parseEtlYamlFile(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        // Configure to handle snake_case property names (load_version -> loadVersion)
        mapper.setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);

        // YAML file has "etl:" root element, so we need to parse it and extract the etl property
        var rootNode = mapper.readTree(file);
        var etlNode = rootNode.get("etl");
        if (etlNode == null) {
            throw new IllegalArgumentException("YAML file must have 'etl:' root element");
        }
        return mapper.treeToValue(etlNode, LoaderData.class);
    }

    private int getCurrentVersion() {
        try {
            String versionStr = jdbcTemplate.queryForObject(
                "SELECT config_value FROM general.system_config WHERE config_key = 'CURRENT_ETL_VERSION'",
                String.class
            );
            return versionStr != null ? Integer.parseInt(versionStr) : 0;
        } catch (Exception e) {
            log.warn("Could not read CURRENT_ETL_VERSION: {}. Assuming version 0.", e.getMessage());
            return 0;
        }
    }

    private void updateCurrentVersion(int newVersion) {
        jdbcTemplate.update(
            "UPDATE general.system_config SET config_value = ?, updated_at = NOW() " +
            "WHERE config_key = 'CURRENT_ETL_VERSION'",
            String.valueOf(newVersion)
        );
    }

    private void markSystemInitialized() {
        jdbcTemplate.update(
            "UPDATE general.system_config SET config_value = 'true', updated_at = NOW() " +
            "WHERE config_key = 'SYSTEM_INITIALIZED'"
        );
    }

    private long createInitializationLog(String fileName, String filePath, long fileSize,
                                         String fileHash, int loadVersion,
                                         String encryptedContent, String status) {
        jdbcTemplate.update(
            "INSERT INTO general.initialization_log " +
            "(file_name, file_path, file_size_bytes, file_hash, load_version, " +
            "file_content_encrypted, status, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())",
            fileName, filePath, fileSize, fileHash, loadVersion, encryptedContent, status
        );

        return jdbcTemplate.queryForObject(
            "SELECT id FROM general.initialization_log WHERE file_hash = ?",
            Long.class,
            fileHash
        );
    }

    private void updateInitializationLog(long id, int sourcesLoaded, int loadersLoaded,
                                         String csvMetadata, String status, String errorMessage) {
        jdbcTemplate.update(
            "UPDATE general.initialization_log SET " +
            "sources_loaded = ?, loaders_loaded = ?, file_metadata = ?, status = ?, " +
            "error_message = ?, processed_at = NOW() WHERE id = ?",
            sourcesLoaded, loadersLoaded, csvMetadata, status, errorMessage, id
        );
    }

    private String generateCsvMetadata(String fileName, int version, int sources, int loaders) {
        String timestamp = CSV_TIMESTAMP_FORMAT.format(Instant.now());
        return String.format("%s,%d,%d,%d,%s", fileName, version, sources, loaders, timestamp);
    }

    private int loadSourceDatabases(LoaderData data, int currentVersion) {
        if (data.getSources() == null || data.getSources().isEmpty()) {
            log.warn("No source databases configured in YAML");
            return 0;
        }

        int count = 0;
        for (LoaderData.SourceDatabaseConfig config : data.getSources()) {
            // Skip if version is not newer than current version
            if (config.getVersion() == null || config.getVersion() <= currentVersion) {
                log.debug("Skipping source database {} (version {} <= current {})",
                    config.getDbCode(), config.getVersion(), currentVersion);
                continue;
            }

            // Check if already exists
            if (sourceDbRepo.findByDbCode(config.getDbCode()).isPresent()) {
                log.info("Source database already exists, skipping: {}", config.getDbCode());
                continue;
            }

            SourceDatabase source = SourceDatabase.builder()
                .dbCode(config.getDbCode())
                .ip(config.getIp())
                .port(config.getPort())
                .dbType(DbType.valueOf(config.getDbType().toUpperCase()))
                .dbName(config.getDbName())
                .userName(config.getUserName())
                .passWord(config.getPassWord())  // Will be encrypted by JPA converter
                .build();

            sourceDbRepo.save(source);
            log.info("Created source database: {} (version {})", config.getDbCode(), config.getVersion());
            count++;
        }

        return count;
    }

    private int loadLoaders(LoaderData data, int currentVersion) {
        if (data.getLoaders() == null || data.getLoaders().isEmpty()) {
            log.warn("No loaders configured in YAML");
            return 0;
        }

        int count = 0;
        for (LoaderData.LoaderConfig config : data.getLoaders()) {
            // Skip if version is not newer than current version
            if (config.getVersion() == null || config.getVersion() <= currentVersion) {
                log.debug("Skipping loader {} (version {} <= current {})",
                    config.getLoaderCode(), config.getVersion(), currentVersion);
                continue;
            }

            // Check if already exists
            if (loaderRepo.findByLoaderCode(config.getLoaderCode()).isPresent()) {
                log.info("Loader already exists, skipping: {}", config.getLoaderCode());
                continue;
            }

            // Check if already has pending approval
            Integer existingApproval = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM loader.approval_request " +
                "WHERE entity_type = 'LOADER' AND entity_id = ? AND approval_status = 'PENDING_APPROVAL'",
                Integer.class,
                config.getLoaderCode()
            );

            if (existingApproval != null && existingApproval > 0) {
                log.info("Loader {} already has pending approval request, skipping", config.getLoaderCode());
                continue;
            }

            // Find source database
            SourceDatabase sourceDb = sourceDbRepo.findByDbCode(config.getSourceDbCode())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Source database not found: " + config.getSourceDbCode()
                ));

            try {
                // Build request data JSON (similar to how LoaderService does it)
                String requestDataJson = buildLoaderRequestJson(config, sourceDb);

                // Submit approval request via JDBC
                String insertApprovalSql = """
                    INSERT INTO loader.approval_request
                    (entity_type, entity_id, request_type, approval_status,
                     requested_by, requested_at, request_data, current_data,
                     change_summary, source, import_label)
                    VALUES (?, ?, ?, ?, ?, NOW(), ?::jsonb, NULL, ?, ?, ?)
                    """;

                jdbcTemplate.update(insertApprovalSql,
                    "LOADER",  // entity_type
                    config.getLoaderCode(),  // entity_id
                    "CREATE",  // request_type
                    "PENDING_APPROVAL",  // approval_status
                    "etl-initializer",  // requested_by
                    requestDataJson,  // request_data
                    "New loader from ETL data file (version " + config.getVersion() + ")",  // change_summary
                    "IMPORT",  // source
                    "etl-data-v" + config.getVersion()  // import_label
                );

                log.info("Submitted approval request for loader: {} (version {})",
                    config.getLoaderCode(), config.getVersion());
                count++;

            } catch (Exception e) {
                log.error("Failed to submit approval request for loader {}: {}",
                    config.getLoaderCode(), e.getMessage(), e);
                throw new RuntimeException("Failed to submit approval request for loader: " + config.getLoaderCode(), e);
            }
        }

        return count;
    }

    /**
     * Build JSON request data for loader approval request.
     * Matches the format expected by LoaderService.
     */
    private String buildLoaderRequestJson(LoaderData.LoaderConfig config, SourceDatabase sourceDb) {
        try {
            // Build a JSON string matching EtlLoaderDto structure
            String json = String.format("""
                {
                  "loaderCode": "%s",
                  "loaderSql": "%s",
                  "sourceDatabaseId": %d,
                  "minIntervalSeconds": %d,
                  "maxIntervalSeconds": %d,
                  "maxQueryPeriodSeconds": %d,
                  "maxParallelExecutions": %d,
                  "loadStatus": "%s",
                  "purgeStrategy": "%s",
                  "aggregationPeriodSeconds": %s,
                  "sourceTimezoneOffsetHours": %d,
                  "enabled": %s
                }
                """,
                escapeJson(config.getLoaderCode()),
                escapeJson(config.getLoaderSql()),
                sourceDb.getId(),
                config.getMinIntervalSeconds() != null ? config.getMinIntervalSeconds() : 10,
                config.getMaxIntervalSeconds() != null ? config.getMaxIntervalSeconds() : 60,
                config.getMaxQueryPeriodSeconds() != null ? config.getMaxQueryPeriodSeconds() : 432000,
                config.getMaxParallelExecutions() != null ? config.getMaxParallelExecutions() : 1,
                config.getLoadStatus() != null ? config.getLoadStatus().toUpperCase() : "IDLE",
                config.getPurgeStrategy() != null ? config.getPurgeStrategy().toUpperCase() : "FAIL_ON_DUPLICATE",
                config.getAggregationPeriodSeconds() != null ? config.getAggregationPeriodSeconds() : "null",
                config.getSourceTimezoneOffsetHours() != null ? config.getSourceTimezoneOffsetHours() : 0,
                config.getEnabled() != null ? config.getEnabled() : true
            );

            return json.trim();

        } catch (Exception e) {
            log.error("Failed to build JSON for loader {}: {}", config.getLoaderCode(), e.getMessage());
            throw new RuntimeException("Failed to build JSON for approval request", e);
        }
    }

    /**
     * Escape special characters for JSON string values.
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    // ===================== AUTH FILE PROCESSING =====================

    private void processAuthFile(File file) throws Exception {
        String fileName = file.getName();
        String filePath = file.getAbsolutePath();
        long fileSize = file.length();

        log.info("Processing auth file: {} ({} bytes)", fileName, fileSize);

        // Read YAML content for encryption
        String yamlContent = Files.readString(file.toPath());
        log.debug("Read YAML content: {} characters", yamlContent.length());

        // Encrypt YAML content using AES-256-GCM
        String encryptedContent = encryptionService.encrypt(yamlContent);
        log.debug("Encrypted YAML content: {} characters", encryptedContent.length());

        // Calculate SHA-256 hash
        String fileHash = calculateFileHash(file);
        log.debug("File hash: {}", fileHash);

        // Check if already processed
        if (isAlreadyProcessed(fileHash)) {
            log.warn("File {} already processed (duplicate hash). Skipping.", fileName);
            moveToProcessed(file);
            return;
        }

        // Parse YAML file
        AuthData data = parseAuthYamlFile(file);

        if (data.getMetadata() == null || data.getMetadata().getLoadVersion() == null) {
            throw new IllegalArgumentException("Missing metadata.load_version in auth YAML file");
        }

        int targetVersion = data.getMetadata().getLoadVersion();
        int currentVersion = getCurrentAuthVersion();

        log.info("Auth file version: {}, Current auth version: {}", targetVersion, currentVersion);

        if (targetVersion <= currentVersion) {
            log.warn("Auth file version {} is not greater than current version {}. Skipping.",
                targetVersion, currentVersion);
            moveToProcessed(file);
            return;
        }

        // Mark as processing (store encrypted YAML content)
        long logId = createInitializationLog(fileName, filePath, fileSize, fileHash,
            targetVersion, encryptedContent, "PROCESSING");

        try {
            // Load users
            int usersLoaded = loadUsers(data, currentVersion);
            log.info("Loaded {} new users", usersLoaded);

            // Generate CSV metadata
            String csvMetadata = generateCsvMetadata(fileName, targetVersion, usersLoaded, 0);

            // Update initialization log
            updateInitializationLog(logId, usersLoaded, 0, csvMetadata, "COMPLETED", null);

            // Update auth version
            updateCurrentAuthVersion(targetVersion);

            log.info("Successfully processed auth file: {} (version {})", fileName, targetVersion);

            // Move to processed
            moveToProcessed(file);

        } catch (Exception e) {
            updateInitializationLog(logId, 0, 0, null, "FAILED", e.getMessage());
            throw e;
        }
    }

    private AuthData parseAuthYamlFile(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        // Configure to handle snake_case property names (load_version -> loadVersion)
        mapper.setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);

        // YAML file has "auth:" root element
        var rootNode = mapper.readTree(file);
        var authNode = rootNode.get("auth");
        if (authNode == null) {
            throw new IllegalArgumentException("YAML file must have 'auth:' root element");
        }
        return mapper.treeToValue(authNode, AuthData.class);
    }

    private int loadUsers(AuthData data, int currentVersion) {
        if (data.getUsers() == null || data.getUsers().isEmpty()) {
            log.warn("No users configured in auth YAML");
            return 0;
        }

        int count = 0;
        for (AuthData.AuthUserConfig user : data.getUsers()) {
            try {
                // Insert user into auth.users table (with ON CONFLICT UPDATE)
                String insertUserSql = """
                    INSERT INTO auth.users
                    (username, password, email, full_name, enabled,
                     account_non_expired, account_non_locked,
                     credentials_non_expired, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'etl-initializer')
                    ON CONFLICT (username) DO UPDATE SET
                        password = EXCLUDED.password,
                        email = EXCLUDED.email,
                        full_name = EXCLUDED.full_name,
                        enabled = EXCLUDED.enabled,
                        account_non_expired = EXCLUDED.account_non_expired,
                        account_non_locked = EXCLUDED.account_non_locked,
                        credentials_non_expired = EXCLUDED.credentials_non_expired,
                        updated_at = NOW(),
                        updated_by = 'etl-initializer'
                    RETURNING id
                    """;

                Long userId = jdbcTemplate.queryForObject(insertUserSql, Long.class,
                    user.getUsername(),
                    user.getPassword(),  // Already BCrypt hashed
                    user.getEmail(),
                    user.getFullName(),
                    user.getEnabled() != null ? user.getEnabled() : true,
                    user.getAccountNonExpired() != null ? user.getAccountNonExpired() : true,
                    user.getAccountNonLocked() != null ? user.getAccountNonLocked() : true,
                    user.getCredentialsNonExpired() != null ? user.getCredentialsNonExpired() : true
                );

                log.info("Created/updated user: {} (id: {})", user.getUsername(), userId);

                // Link user to roles
                if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                    for (String roleName : user.getRoles()) {
                        String linkRoleSql = """
                            INSERT INTO auth.user_roles (user_id, role_id, granted_by)
                            SELECT ?, r.id, 'etl-initializer'
                            FROM auth.roles r
                            WHERE r.role_name = ?
                            ON CONFLICT (user_id, role_id) DO NOTHING
                            """;

                        int rowsAffected = jdbcTemplate.update(linkRoleSql, userId, roleName);
                        if (rowsAffected > 0) {
                            log.info("Linked user {} to role {}", user.getUsername(), roleName);
                        } else {
                            log.debug("User {} already has role {} or role doesn't exist",
                                user.getUsername(), roleName);
                        }
                    }
                }

                count++;

            } catch (Exception e) {
                log.error("Failed to create user {}: {}", user.getUsername(), e.getMessage(), e);
                throw new RuntimeException("Failed to create user: " + user.getUsername(), e);
            }
        }

        return count;
    }

    private int getCurrentAuthVersion() {
        try {
            String versionStr = jdbcTemplate.queryForObject(
                "SELECT config_value FROM general.system_config WHERE config_key = 'CURRENT_AUTH_VERSION'",
                String.class
            );
            return versionStr != null ? Integer.parseInt(versionStr) : 0;
        } catch (Exception e) {
            log.warn("Could not read CURRENT_AUTH_VERSION: {}. Assuming version 0.", e.getMessage());
            return 0;
        }
    }

    private void updateCurrentAuthVersion(int newVersion) {
        // Insert or update auth version
        jdbcTemplate.update(
            """
            INSERT INTO general.system_config (config_key, config_value, description, created_at, updated_at)
            VALUES ('CURRENT_AUTH_VERSION', ?, 'Current authentication data version', NOW(), NOW())
            ON CONFLICT (config_key) DO UPDATE SET
                config_value = EXCLUDED.config_value,
                updated_at = NOW()
            """,
            String.valueOf(newVersion)
        );
    }

    // ===================== END AUTH FILE PROCESSING =====================

    // ===================== MESSAGES FILE PROCESSING =====================

    private void processMessagesFile(File file) throws Exception {
        String fileName = file.getName();
        String filePath = file.getAbsolutePath();
        long fileSize = file.length();

        log.info("Processing messages file: {} ({} bytes)", fileName, fileSize);

        // Read YAML content for encryption
        String yamlContent = Files.readString(file.toPath());
        log.debug("Read YAML content: {} characters", yamlContent.length());

        // Encrypt YAML content using AES-256-GCM
        String encryptedContent = encryptionService.encrypt(yamlContent);
        log.debug("Encrypted YAML content: {} characters", encryptedContent.length());

        // Calculate SHA-256 hash
        String fileHash = calculateFileHash(file);
        log.debug("File hash: {}", fileHash);

        // Check if already processed
        if (isAlreadyProcessed(fileHash)) {
            log.warn("File {} already processed (duplicate hash). Skipping.", fileName);
            moveToProcessed(file);
            return;
        }

        // Parse YAML file
        MessageData data = parseMessagesYamlFile(file);

        if (data.getMetadata() == null || data.getMetadata().getLoadVersion() == null) {
            throw new IllegalArgumentException("Missing metadata.load_version in messages YAML file");
        }

        int targetVersion = data.getMetadata().getLoadVersion();
        int currentVersion = getCurrentMessagesVersion();

        log.info("Messages file version: {}, Current messages version: {}", targetVersion, currentVersion);

        if (targetVersion <= currentVersion) {
            log.warn("Messages file version {} is not greater than current version {}. Skipping.",
                targetVersion, currentVersion);
            moveToProcessed(file);
            return;
        }

        // Mark as processing (store encrypted YAML content)
        long logId = createInitializationLog(fileName, filePath, fileSize, fileHash,
            targetVersion, encryptedContent, "PROCESSING");

        try {
            // Load messages
            int messagesLoaded = loadMessages(data, currentVersion);
            log.info("Loaded {} new messages", messagesLoaded);

            // Generate CSV metadata
            String csvMetadata = generateCsvMetadata(fileName, targetVersion, messagesLoaded, 0);

            // Update initialization log
            updateInitializationLog(logId, messagesLoaded, 0, csvMetadata, "COMPLETED", null);

            // Update messages version
            updateCurrentMessagesVersion(targetVersion);

            log.info("Successfully processed messages file: {} (version {})", fileName, targetVersion);

            // Move to processed
            moveToProcessed(file);

        } catch (Exception e) {
            updateInitializationLog(logId, 0, 0, null, "FAILED", e.getMessage());
            throw e;
        }
    }

    private MessageData parseMessagesYamlFile(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        // Configure to handle snake_case property names (load_version -> loadVersion)
        mapper.setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);

        // YAML file has "messages:" root element
        var rootNode = mapper.readTree(file);
        var messagesNode = rootNode.get("messages");
        if (messagesNode == null) {
            throw new IllegalArgumentException("YAML file must have 'messages:' root element");
        }
        return mapper.treeToValue(messagesNode, MessageData.class);
    }

    private int loadMessages(MessageData data, int currentVersion) {
        if (data.getMessages() == null || data.getMessages().isEmpty()) {
            log.warn("No messages configured in messages YAML");
            return 0;
        }

        int count = 0;
        for (MessageData.MessageConfig message : data.getMessages()) {
            try {
                // Insert message into general.message_dictionary table (with ON CONFLICT UPDATE)
                String insertMessageSql = """
                    INSERT INTO general.message_dictionary
                    (message_code, message_category, message_en, message_ar,
                     description, created_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (message_code) DO UPDATE SET
                        message_category = EXCLUDED.message_category,
                        message_en = EXCLUDED.message_en,
                        message_ar = EXCLUDED.message_ar,
                        description = EXCLUDED.description,
                        updated_at = NOW(),
                        updated_by = 'etl-initializer'
                    """;

                jdbcTemplate.update(insertMessageSql,
                    message.getMessageCode(),
                    message.getMessageCategory(),
                    message.getMessageEn(),
                    message.getMessageAr(),
                    message.getDescription(),
                    message.getCreatedBy() != null ? message.getCreatedBy() : "system"
                );

                log.info("Created/updated message: {}", message.getMessageCode());
                count++;

            } catch (Exception e) {
                log.error("Failed to create message {}: {}", message.getMessageCode(), e.getMessage(), e);
                throw new RuntimeException("Failed to create message: " + message.getMessageCode(), e);
            }
        }

        return count;
    }

    private int getCurrentMessagesVersion() {
        try {
            String versionStr = jdbcTemplate.queryForObject(
                "SELECT config_value FROM general.system_config WHERE config_key = 'CURRENT_MESSAGES_VERSION'",
                String.class
            );
            return versionStr != null ? Integer.parseInt(versionStr) : 0;
        } catch (Exception e) {
            log.warn("Could not read CURRENT_MESSAGES_VERSION: {}. Assuming version 0.", e.getMessage());
            return 0;
        }
    }

    private void updateCurrentMessagesVersion(int newVersion) {
        // Insert or update messages version
        jdbcTemplate.update(
            """
            INSERT INTO general.system_config (config_key, config_value, description, created_at, updated_at)
            VALUES ('CURRENT_MESSAGES_VERSION', ?, 'Current message dictionary data version', NOW(), NOW())
            ON CONFLICT (config_key) DO UPDATE SET
                config_value = EXCLUDED.config_value,
                updated_at = NOW()
            """,
            String.valueOf(newVersion)
        );
    }

    // ===================== END MESSAGES FILE PROCESSING =====================

    private void moveToProcessed(File file) {
        try {
            Path source = file.toPath();
            Path target = Paths.get(processedPath, file.getName());
            Files.createDirectories(Paths.get(processedPath));
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Moved file to processed: {}", target);
        } catch (IOException e) {
            log.error("Failed to move file to processed directory: {}", e.getMessage());
        }
    }

    private void moveToFailed(File file, String errorMessage) {
        try {
            Path source = file.toPath();
            Path target = Paths.get(failedPath, file.getName());
            Files.createDirectories(Paths.get(failedPath));
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.warn("Moved file to failed: {} (error: {})", target, errorMessage);
        } catch (IOException e) {
            log.error("Failed to move file to failed directory: {}", e.getMessage());
        }
    }
}
