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

import javax.swing.*;
import java.awt.*;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class TranslationTarget<J extends Component> {
    public static final TranslationTarget<JLabel> LABEL_TEXT = new TranslationTarget<>(JLabel::setText);
    public static final TranslationTarget<AbstractButton> BUTTON_TEXT = new TranslationTarget<>(AbstractButton::setText);
    public static final TranslationTarget<JComponent> TOOLTIP = new TranslationTarget<>(JComponent::setToolTipText);

    private static final Map<TranslationTarget, TranslationTarget> HTML_TARGETS = new IdentityHashMap<>();

    public static <J extends Component> TranslationTarget<J> html(TranslationTarget<J> target) {
        return (TranslationTarget<J>) HTML_TARGETS.computeIfAbsent(target, k -> new TranslationTarget((c, s) -> k.setter.accept(c, "<html>" + s + "</html>")));
    }

    final BiConsumer<? super J, String> setter;

    public TranslationTarget(BiConsumer<J, String> setter) {
        this.setter = setter;
    }
}
