/*
 * =============================================================================
 * Copyright (c) 2022 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 * =============================================================================
 */
package io.openliberty.inspect;

import java.io.FileInputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;

public final class Feature implements Comparable<Feature> {
    private final String fullName;
    private final String shortName;
    private final String name;
    private final Visibility visibility;
    private final List<String> containedFeatures;
    private final boolean isAutoFeature;

    Feature(Path p) {
        final Attributes attributes;
        try (InputStream in = new FileInputStream(p.toFile())) {
            attributes = new Manifest(in).getMainAttributes();
        } catch (IOException e) {
            throw new IOError(e);
        }
        Optional<ValueElement> symbolicName = Key.SUBSYSTEM_SYMBOLICNAME.parseValues(attributes).findFirst();
        this.fullName = symbolicName.orElseThrow(Error::new).id;
        this.shortName = Key.IBM_SHORTNAME.get(attributes).orElse(null);
        this.visibility = symbolicName
                .map(v -> v.getQualifier("visibility"))
                .map(String::toUpperCase)
                .map(Visibility::valueOf)
                .orElse(Visibility.UNKNOWN);
        this.name = visibility == Visibility.PUBLIC ? shortName().orElse(fullName) : fullName;
        this.containedFeatures = Key.SUBSYSTEM_CONTENT.parseValues(attributes)
                .filter(v -> "osgi.subsystem.feature".equals(v.getQualifier("type")))
                .map(v -> v.id)
                .collect(Collectors.toUnmodifiableList());
        this.isAutoFeature = Key.IBM_PROVISION_CAPABILITY.isPresent(attributes);
    }

    public String fullName() { return fullName; }
    public Optional<String> shortName() { return Optional.ofNullable(shortName); }
    public Visibility visibility() { return this.visibility; }
    public String name() { return name; }
    public Stream<String> containedFeatures() { return containedFeatures.stream(); }
    public String displayName() { return (isAutoFeature ? "&" : visibility.indicator) + name(); }

    public String simpleName() {
        return shortName().orElseGet(() -> fullName
                .replaceFirst("^com.ibm.websphere.app(server|client).", "")
                .replaceFirst("^io.openliberty.", ""));
    }

    public boolean matches(String pattern) {
        pattern = requireNonNull(pattern).toLowerCase();
        if (!pattern.contains(":")) pattern = "glob:" + pattern;
        var matcher = FileSystems.getDefault().getPathMatcher(pattern);
        return shortName()
                .map(String::toLowerCase)
                .map(Paths::get)
                .map(matcher::matches)
                .filter(TRUE::equals)
                .orElseGet(() -> matcher.matches(Paths.get(fullName().toLowerCase())));
    }

    @Override
    public int compareTo(Feature that) {
        int result = Boolean.compare(this.isAutoFeature, that.isAutoFeature);
        if (0 == result) result = this.visibility().compareTo(that.visibility());
        if (0 == result) result = this.name().compareTo(that.name());
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null) return false;
        if (! (other instanceof Feature)) return false;
        Feature that = (Feature) other;
        return this.fullName.equals(that.fullName);
    }

    @Override
    public int hashCode() { return Objects.hash(fullName); }

    @Override
    public String toString() { return fullName(); }
}
