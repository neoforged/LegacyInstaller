package net.minecraftforge.installer.actions;

import java.io.File;
import java.util.Objects;
import java.util.function.Supplier;

@FunctionalInterface
public interface TargetValidator {

    ValidationResult validate(File target);

    default TargetValidator and(TargetValidator other) {
        return target -> validate(target)
                .combine(() -> other.validate(target));
    }

    static TargetValidator isDirectory() {
        return target -> target.isDirectory() ? ValidationResult.valid() : ValidationResult.invalid(true, "The specified path needs to be a directory");
    }

    static TargetValidator shouldExist(boolean critical) {
        return target -> target.exists() ? ValidationResult.valid() : ValidationResult.invalid(critical, "The specified directory does not exist" + (critical ? "" : "<br/>It will be created"));
    }

    static TargetValidator shouldBeEmpty() {
        return target -> Objects.requireNonNull(target.list()).length == 0 ? ValidationResult.valid() : ValidationResult.invalid(false, "There are already files in the target directory");
    }

    static TargetValidator isMCInstallationDirectory() {
        return target -> (new File(target, "launcher_profiles.json").exists() ||
                new File(target, "launcher_profiles_microsoft_store.json").exists()) ? ValidationResult.valid() : ValidationResult.invalid(true, "The directory is missing a launcher profile. Please run the minecraft launcher first");
    }

    class ValidationResult {
        private static final ValidationResult VALID = new ValidationResult(true, false, "");

        public final boolean valid, critical;
        public final String message;

        private ValidationResult(boolean valid, boolean critical, String message) {
            this.valid = valid;
            this.critical = critical;
            this.message = message;
        }

        public static ValidationResult valid() {
            return VALID;
        }

        public static ValidationResult invalid(boolean critical, String message) {
            return new ValidationResult(false, critical, message);
        }

        public ValidationResult combine(Supplier<ValidationResult> other) {
            return this.valid ? other.get() : this;
        }
    }
}
