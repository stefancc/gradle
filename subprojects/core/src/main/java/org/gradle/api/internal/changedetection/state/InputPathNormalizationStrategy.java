/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.file.FileType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum InputPathNormalizationStrategy implements PathNormalizationStrategy {
    /**
     * Use the absolute path of the files.
     */
    ABSOLUTE {
        @Override
        public boolean isPathAbsolute() {
            return true;
        }

        @Nonnull
        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(FileSnapshot fileSnapshot, StringInterner stringInterner) {
            return new NonNormalizedFileSnapshot(fileSnapshot.getPath(), fileSnapshot.getContent());
        }
    },

    /**
     * Use the location of the file related to a hierarchy.
     */
    RELATIVE {
        @Override
        public boolean isPathAbsolute() {
            return false;
        }

        @Nonnull
        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(FileSnapshot fileSnapshot, StringInterner stringInterner) {
            // Ignore path of root directories, use base name of root files
            if (fileSnapshot.isRoot() && fileSnapshot.getType() == FileType.Directory) {
                return new IgnoredPathFileSnapshot(fileSnapshot.getContent());
            }
            return getRelativeSnapshot(fileSnapshot);
        }
    },

    /**
     * Use the file name only.
     */
    NAME_ONLY {
        @Override
        public boolean isPathAbsolute() {
            return false;
        }

        @Nonnull
        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(FileSnapshot fileSnapshot, StringInterner stringInterner) {
            // Ignore path of root directories
            if (fileSnapshot.isRoot() && fileSnapshot.getType() == FileType.Directory) {
                return new IgnoredPathFileSnapshot(fileSnapshot.getContent());
            }
            return getRelativeSnapshot(fileSnapshot, fileSnapshot.getName().length());
        }
    },

    /**
     * Ignore the file path completely.
     */
    NONE {
        @Override
        public boolean isPathAbsolute() {
            return false;
        }

        @Nullable
        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(FileSnapshot fileSnapshot, StringInterner stringInterner) {
            if (fileSnapshot.getType() == FileType.Directory) {
                return null;
            }
            return new IgnoredPathFileSnapshot(fileSnapshot.getContent());
        }
    };

    public static InputPathNormalizationStrategy valueOf(PathSensitivity pathSensitivity) {
        switch (pathSensitivity) {
            case ABSOLUTE:
                return ABSOLUTE;
            case RELATIVE:
                return RELATIVE;
            case NAME_ONLY:
                return NAME_ONLY;
            case NONE:
                return NONE;
            default:
                throw new IllegalArgumentException("Unknown path usage: " + pathSensitivity);
        }
    }

    @VisibleForTesting
    static NormalizedFileSnapshot getRelativeSnapshot(final FileSnapshot fileSnapshot) {
        int length = 0;
        for (String segment : fileSnapshot.getRelativePath().getSegments()) {
            length += segment.length();
        }
        length += fileSnapshot.getRelativePath().getSegments().length - 1;
        return getRelativeSnapshot(fileSnapshot, length);
    }

    static NormalizedFileSnapshot getRelativeSnapshot(FileSnapshot fileSnapshot, int normalizedPathLength) {
        FileContentSnapshot contentSnapshot = fileSnapshot.getContent();
        String absolutePath = fileSnapshot.getPath();
        return new IndexedNormalizedFileSnapshot(absolutePath, absolutePath.length() - normalizedPathLength, contentSnapshot);
    }
}
