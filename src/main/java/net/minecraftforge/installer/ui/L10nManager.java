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
package net.minecraftforge.installer.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

public class L10nManager {
    public static final ResourceBundle.Control CONTROL = new ResourceBundle.Control() {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload) throws IllegalAccessException, InstantiationException, IOException {
            if (format.equals("java.class")) {
                return super.newBundle(baseName, locale, format, loader, reload);
            } else if (!format.equals("java.properties")) {
                throw new IllegalArgumentException("unknown format: " + format);
            }

            final String bundleName = toBundleName(baseName, locale);

            if (bundleName.contains("://")) return null;
            final String resourceName = toResourceName(bundleName, "xml");

            InputStream is = null;
            if (reload) {
                URL url = loader.getResource(resourceName);
                if (url != null) {
                    URLConnection connection = url.openConnection();
                    if (connection != null) {
                        // Disable caches to get fresh data for
                        // reloading.
                        connection.setUseCaches(false);
                        is = connection.getInputStream();
                    }
                }
            } else {
                is = loader.getResourceAsStream(resourceName);
            }

            if (is == null) return null;
            try (final InputStream i = is) {
                final Properties props = new Properties();
                props.loadFromXML(i);
                final Map<String, Object> lookup = new HashMap(props);
                return new ResourceBundle() {

                    @Override
                    protected Object handleGetObject(@NotNull String key) {
                        return lookup.get(key);
                    }

                    @NotNull
                    @Override
                    public Enumeration<String> getKeys() {
                        return new MergedEnumeration(lookup.keySet(), (parent != null) ? parent.getKeys() : null);
                    }

                    @NotNull
                    @Override
                    protected Set<String> handleKeySet() {
                        return lookup.keySet();
                    }
                };
            }
        }
    };

    private final Map<Component, Map<TranslationTarget<?>, PropertyChangeListener>> components = new IdentityHashMap<>();
    private Locale locale;
    private ResourceBundle bundle;
    private final String bundleName;
    private final Path settingsFile;
    private final List<LocaleSelection> known;

    public L10nManager(String bundleName, File settingsFileIn) {
        this.bundleName = bundleName;
        this.settingsFile = settingsFileIn.toPath();
        this.known = Collections.unmodifiableList(computeKnownLocales());

        if (Files.exists(settingsFile)) {
            try (final InputStream is = Files.newInputStream(settingsFile)) {
                final Properties props = new Properties();
                props.load(is);
                final String lang = props.getProperty("language");
                if (lang == null) {
                    setDefaultLocale();
                } else {
                    setLocale(Locale.forLanguageTag(lang), false);
                }
            } catch (Exception exception) {
                System.err.println("Failed to read settings file: " + exception);
                setDefaultLocale();
            }
        } else {
            setDefaultLocale();
        }
    }

    private void setDefaultLocale() {
        final String expected = Locale.getDefault().toLanguageTag();
        setLocale(known.stream().filter(se -> se.locale.toLanguageTag().equals(expected))
                .findFirst().map(l -> l.locale).orElse(Locale.ENGLISH), false);
    }

    public JButton button(String key, Object... args) {
        return translate(new JButton(), TranslationTarget.BUTTON_TEXT, key, args);
    }

    public JLabel label(String key, Object... args) {
        return translate(new JLabel(), TranslationTarget.LABEL_TEXT, key, args);
    }

    public JRadioButton radioButton(AbstractAction action, String key, Object... args) {
        final JRadioButton button = new JRadioButton();
        button.setAction(action);
        return translate(button, TranslationTarget.BUTTON_TEXT, key, args);
    }

    public <T extends JComponent> T setTooltip(T component, String key, Object... args) {
        return translate(component, TranslationTarget.TOOLTIP, key, args);
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> T translate(T component, TranslationTarget<? super T> target, String key, Object... args) {
        final Map<TranslationTarget<? super T>, PropertyChangeListener> listeners = (Map) components.computeIfAbsent(component, k -> new HashMap());
        if (listeners.containsKey(target)) {
            component.removePropertyChangeListener(listeners.get(target));
        }
        final PropertyChangeListener pcl = evt -> target.setter.accept(component, translate(key, args));
        component.addPropertyChangeListener("locale", pcl);
        if (component.getLocale().equals(locale)) {
            target.setter.accept(component, translate(key, args));
        } else {
            component.setLocale(locale);
        }
        listeners.put(target, pcl);
        return component;
    }

    public synchronized String translate(String key, Object... args) {
        try {
            return String.format(bundle.getString(key), args);
        } catch (MissingResourceException ignored) {
            return String.format(key, args);
        }
    }

    public String translate(TranslatedMessage message) {
        return translate(message.key, message.arguments);
    }

    public synchronized void setLocale(Locale locale, boolean write) {
        this.locale = locale;
        this.bundle = ResourceBundle.getBundle(bundleName, locale, CONTROL);

        components.keySet().forEach(comp -> comp.setLocale(locale));

        if (write) {
            try {
                Files.createDirectories(settingsFile.getParent());
                try (final OutputStream os = Files.newOutputStream(settingsFile)) {
                    final Properties props = new Properties();
                    props.put("language", locale.toLanguageTag());
                    props.store(os, "NeoForge installer settings file");
                }
                Files.setAttribute(settingsFile, "dos:hidden", true);
            } catch (Exception exception) {
                System.err.println("Failed to write settings file: " + exception);
            }
        }
    }

    public synchronized Locale getLocale() {
        return locale;
    }

    public List<LocaleSelection> getKnownLocales() {
        return known;
    }

    private List<LocaleSelection> computeKnownLocales() {
        try {
            final List<LocaleSelection> selections = new ArrayList<>();
            for (final Locale locale : Locale.getAvailableLocales()) {
                final InputStream is = L10nManager.class.getResourceAsStream("/" + CONTROL.toBundleName(bundleName, locale) + ".xml");
                if (is != null) {
                    try {
                        final Properties properties = new Properties();
                        properties.loadFromXML(is);
                        selections.add(new LocaleSelection(locale, properties.getProperty("language.name")));
                    } finally {
                        is.close();
                    }
                }
            }
            return selections;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to read languages: " + exception.getMessage(), exception);
        }
    }

    public static final class LocaleSelection {
        public final Locale locale;
        public final String name;

        private LocaleSelection(Locale locale, String name) {
            this.locale = locale;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final class MergedEnumeration implements Enumeration<String> {

        private final Set<String> set;
        private final Iterator<String> iterator;
        private final Enumeration<String> enumeration;

        public MergedEnumeration(Set<String> set, Enumeration<String> enumeration) {
            this.set = set;
            this.iterator = set.iterator();
            this.enumeration = enumeration;
        }

        String next = null;

        public boolean hasMoreElements() {
            if (next == null) {
                if (iterator.hasNext()) {
                    next = iterator.next();
                } else if (enumeration != null) {
                    while (next == null && enumeration.hasMoreElements()) {
                        next = enumeration.nextElement();
                        if (set.contains(next)) {
                            next = null;
                        }
                    }
                }
            }
            return next != null;
        }

        public String nextElement() {
            if (hasMoreElements()) {
                String result = next;
                next = null;
                return result;
            } else {
                throw new NoSuchElementException();
            }
        }
    }
}
