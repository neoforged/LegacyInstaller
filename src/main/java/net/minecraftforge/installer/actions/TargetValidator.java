/*
 * Installer
 * Copyright (c) 2016-2018.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.minecraftforge.installer.actions;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A functional interface responsible for checking whether a target installation directory is valid.
 */
@FunctionalInterface
public interface TargetValidator {

    @NotNull
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

        /**
         * Whether the target directory is valid for installation.
         */
        public final boolean valid;
        /**
         * If {@code true}, the installation cannot begin.
         */
        public final boolean critical;
        /**
         * A message to display to users if the target is invalid.
         */
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
