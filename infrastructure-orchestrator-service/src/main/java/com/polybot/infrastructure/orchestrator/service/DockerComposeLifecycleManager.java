package com.polybot.infrastructure.orchestrator.service;

import com.polybot.infrastructure.orchestrator.config.InfrastructureProperties;
import com.polybot.infrastructure.orchestrator.config.InfrastructureProperties.DockerComposeStack;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerComposeLifecycleManager {

    private final InfrastructureProperties properties;
    private volatile boolean stacksRunning = false;

    @PostConstruct
    public void startInfrastructureStacks() {
        log.info("Starting infrastructure stacks lifecycle manager");
        try {
            // Check if stacks are configured
            if (properties.getStacks() == null || properties.getStacks().isEmpty()) {
                log.warn("No infrastructure stacks configured. Skipping stack startup.");
                return;
            }

            // Sort stacks by startup order
            List<DockerComposeStack> orderedStacks = properties.getStacks().stream()
                .sorted(Comparator.comparingInt(DockerComposeStack::getStartupOrder))
                .toList();

            for (DockerComposeStack stack : orderedStacks) {
                log.info("═══════════════════════════════════════════");
                log.info("Starting stack: {}", stack.getName());
                log.info("═══════════════════════════════════════════");

                validateDockerComposeFile(stack);
                cleanupExistingContainers(stack);
                startStack(stack);
                waitForStackReadiness(stack);

                log.info("✓ Stack '{}' is UP and READY", stack.getName());
            }

            stacksRunning = true;
            log.info("═══════════════════════════════════════════");
            log.info("✓ All infrastructure stacks are UP and READY");
            log.info("═══════════════════════════════════════════");
        } catch (Exception e) {
            log.error("Failed to start infrastructure stacks", e);
            throw new RuntimeException("Failed to start infrastructure stacks", e);
        }
    }

    @PreDestroy
    public void stopInfrastructureStacks() {
        if (!stacksRunning) {
            log.info("Infrastructure stacks are not running, skipping shutdown");
            return;
        }

        // Check if stacks are configured
        if (properties.getStacks() == null || properties.getStacks().isEmpty()) {
            log.info("No infrastructure stacks configured, skipping shutdown");
            return;
        }

        log.info("Stopping infrastructure stacks");

        // Stop in reverse order
        List<DockerComposeStack> reverseOrderStacks = properties.getStacks().stream()
            .sorted(Comparator.comparingInt(DockerComposeStack::getStartupOrder).reversed())
            .toList();

        for (DockerComposeStack stack : reverseOrderStacks) {
            try {
                log.info("Stopping stack: {}", stack.getName());
                stopStack(stack);
                log.info("✓ Stack '{}' stopped", stack.getName());
            } catch (Exception e) {
                log.error("Failed to stop stack '{}' cleanly", stack.getName(), e);
            }
        }

        stacksRunning = false;
        log.info("✓ All infrastructure stacks stopped");
    }

    private void validateDockerComposeFile(DockerComposeStack stack) throws IOException {
        Path composePath = resolveComposeFilePath(stack);
        if (!Files.exists(composePath)) {
            throw new IllegalStateException(
                "Docker Compose file not found: " + composePath +
                " for stack '" + stack.getName() + "'" +
                "\nCurrent working directory: " + Paths.get(".").toAbsolutePath()
            );
        }
        log.info("Found docker-compose file at: {}", composePath);
        // Update stack with resolved path
        stack.setFilePath(composePath.toString());
    }

    private Path resolveComposeFilePath(DockerComposeStack stack) {
        String configuredPath = stack.getFilePath();
        log.debug("Resolving Docker Compose file path for stack '{}': {}", stack.getName(), configuredPath);
        
        Path currentDir = Paths.get(".").toAbsolutePath().normalize();
        log.debug("Current working directory: {}", currentDir);
        
        // Normalize the path string (remove ./ prefix if present, handle absolute paths incorrectly starting with /)
        String normalizedPath = configuredPath;
        if (normalizedPath.startsWith("./")) {
            normalizedPath = normalizedPath.substring(2);
            log.debug("Removed './' prefix, normalized path: {}", normalizedPath);
        }
        
        // If path starts with / but file doesn't exist, it might be a misconfigured absolute path
        // Extract just the filename and try relative to current directory
        if (normalizedPath.startsWith("/") && !normalizedPath.equals("/")) {
            String filename = normalizedPath.substring(1); // Remove leading /
            log.debug("Path starts with / but may be misconfigured, trying filename: {}", filename);
            
            // Try relative to current working directory first
            Path cwdPath = currentDir.resolve(filename).normalize();
            log.debug("Trying path relative to CWD: {}", cwdPath);
            if (Files.exists(cwdPath)) {
                log.debug("Found file at CWD relative path: {}", cwdPath);
                return cwdPath;
            }
        }
        
        Path path = Paths.get(normalizedPath);

        // If absolute path exists, use it
        if (path.isAbsolute() && Files.exists(path)) {
            log.debug("Found absolute path: {}", path);
            return path;
        }

        // Try relative to current working directory
        Path cwdPath = currentDir.resolve(normalizedPath).normalize();
        log.debug("Trying path relative to CWD: {}", cwdPath);
        if (Files.exists(cwdPath)) {
            log.debug("Found file at CWD relative path: {}", cwdPath);
            return cwdPath;
        }

        // Try relative to project root (go up from module directory)
        Path projectRootPath = currentDir.getParent();
        if (projectRootPath != null) {
            Path parentPath = projectRootPath.resolve(normalizedPath).normalize();
            log.debug("Trying path relative to parent directory: {}", parentPath);
            if (Files.exists(parentPath)) {
                log.debug("Found file at parent relative path: {}", parentPath);
                return parentPath;
            }
        }

        // Try from project root (if we're in a subdirectory, go up to find project root)
        // Check if current directory name suggests we're in a service subdirectory
        String currentDirName = currentDir.getFileName().toString();
        if (currentDirName.endsWith("-service")) {
            Path projectRoot = currentDir.getParent();
            if (projectRoot != null) {
                Path projectRootFile = projectRoot.resolve(normalizedPath).normalize();
                log.debug("Trying path from project root (detected service directory): {}", projectRootFile);
                if (Files.exists(projectRootFile)) {
                    log.debug("Found file at project root: {}", projectRootFile);
                    return projectRootFile;
                }
            }
        }

        // Return the CWD path (will fail in validation, but at least it's a valid path)
        log.warn("Could not find Docker Compose file, will return CWD path for validation error: {}", cwdPath);
        return cwdPath;
    }

    private void cleanupExistingContainers(DockerComposeStack stack) {
        log.info("Cleaning up any existing containers...");
        try {
            // Always try to clean up, using --remove-orphans to handle containers from previous runs
            ProcessBuilder pb = new ProcessBuilder(
                "docker", "compose",
                "-f", stack.getFilePath(),
                "-p", stack.getProjectName(),
                "down", "--remove-orphans"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            
            // Log output for debugging
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("docker-compose down: {}", line);
                }
            }
            
            boolean completed = process.waitFor(30, TimeUnit.SECONDS);
            if (completed) {
                int exitCode = process.exitValue();
                if (exitCode == 0) {
                    log.debug("Cleanup completed successfully");
                } else {
                    // Exit code 1 might mean no containers existed, which is fine
                    log.debug("Cleanup completed with exit code: {} (this is usually fine if no containers existed)", exitCode);
                }
            } else {
                log.warn("Cleanup timed out after 30 seconds, but continuing anyway");
                process.destroyForcibly();
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup existing containers, continuing anyway", e);
        }
    }

    private void startStack(DockerComposeStack stack) throws IOException, InterruptedException {
        log.info("Executing: docker compose up -d");

        ProcessBuilder pb = new ProcessBuilder(
            "docker", "compose",
            "-f", stack.getFilePath(),
            "-p", stack.getProjectName(),
            "up", "-d"
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Log output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("docker-compose: {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Docker Compose up failed with exit code: " + exitCode);
        }
    }

    private void waitForStackReadiness(DockerComposeStack stack) throws InterruptedException, IOException {
        log.info("Waiting for services to become healthy...");

        int attempts = properties.getStartupTimeoutSeconds() / properties.getHealthCheckIntervalSeconds();

        for (int i = 0; i < attempts; i++) {
            if (isStackHealthy(stack)) {
                log.info("All services are healthy after {} seconds",
                    (i + 1) * properties.getHealthCheckIntervalSeconds());
                return;
            }

            log.debug("Health check {}/{} - waiting {} seconds...",
                i + 1, attempts, properties.getHealthCheckIntervalSeconds());
            TimeUnit.SECONDS.sleep(properties.getHealthCheckIntervalSeconds());
        }

        log.warn("Some services may not be fully healthy after {} seconds, but continuing...",
            properties.getStartupTimeoutSeconds());
    }

    private boolean isStackHealthy(DockerComposeStack stack) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "compose",
            "-f", stack.getFilePath(),
            "-p", stack.getProjectName(),
            "ps", "--format", "json"
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        }

        process.waitFor();

        // Check if all expected services are running
        long runningCount = output.stream()
            .filter(line -> line.contains("\"State\":\"running\"") || line.contains("\"running\""))
            .count();

        return runningCount >= stack.getExpectedServices();
    }

    private void stopStack(DockerComposeStack stack) throws IOException, InterruptedException {
        log.debug("Executing: docker compose down");

        ProcessBuilder pb = new ProcessBuilder(
            "docker", "compose",
            "-f", stack.getFilePath(),
            "-p", stack.getProjectName(),
            "down"
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.debug("docker-compose: {}", line);
            }
        }

        process.waitFor(30, TimeUnit.SECONDS);
    }

    public InfrastructureStatus getInfrastructureStatus() {
        List<StackStatus> stackStatuses = new ArrayList<>();

        if (properties.getStacks() == null || properties.getStacks().isEmpty()) {
            return new InfrastructureStatus(
                stacksRunning,
                "NO_STACKS_CONFIGURED",
                stackStatuses
            );
        }

        for (DockerComposeStack stack : properties.getStacks()) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "docker", "compose",
                    "-f", stack.getFilePath(),
                    "-p", stack.getProjectName(),
                    "ps", "--format", "json"
                );
                pb.redirectErrorStream(true);

                Process process = pb.start();

                List<String> services = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        services.add(line);
                    }
                }

                process.waitFor();

                long runningCount = services.stream()
                    .filter(line -> line.contains("\"State\":\"running\"") || line.contains("\"running\""))
                    .count();

                String healthStatus = runningCount >= stack.getExpectedServices() ? "HEALTHY" : "DEGRADED";

                stackStatuses.add(new StackStatus(
                    stack.getName(),
                    services.size(),
                    (int) runningCount,
                    stack.getExpectedServices(),
                    healthStatus
                ));

            } catch (Exception e) {
                log.error("Failed to get status for stack '{}'", stack.getName(), e);
                stackStatuses.add(new StackStatus(
                    stack.getName(),
                    0,
                    0,
                    stack.getExpectedServices(),
                    "ERROR: " + e.getMessage()
                ));
            }
        }

        boolean allHealthy = stackStatuses.stream()
            .allMatch(s -> s.healthStatus.equals("HEALTHY"));

        return new InfrastructureStatus(
            stacksRunning,
            allHealthy ? "HEALTHY" : "DEGRADED",
            stackStatuses
        );
    }

    public record InfrastructureStatus(
        boolean managed,
        String overallHealth,
        List<StackStatus> stacks
    ) {}

    public record StackStatus(
        String name,
        int totalServices,
        int runningServices,
        int expectedServices,
        String healthStatus
    ) {}
}
