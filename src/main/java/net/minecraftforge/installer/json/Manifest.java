/*
 * Installer
 * Copyright (c) 2016-2018.
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
package net.minecraftforge.installer.json;

import java.util.List;

public class Manifest {
    private List<Info> versions;

    public Info get(String version) {
        return versions == null ? null : versions.stream().filter(v -> version.equals(v.getId())).findFirst().orElse(null);
    }

    public static class Info {
        private String id;
        private String url;
        public String sha1;

        public String getId() {
            return id;
        }

        public String getUrl() {
            return url;
        }
    }
}
