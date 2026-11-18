package io.github.kreader.feature.reader

object EpubStyleNormalizer {

    private val ABSOLUTE_FONT_SIZE_REGEX = Regex("""(?i)^\s*([\d.]+)(px|pt|in|cm|mm|pc)\s*$""")
    private val FONT_SIZE_PROPERTY_REGEX = Regex("""(?i)\bfont-size\s*:\s*([^;!}]+)(\s*!important)?""")

    /**
     * Normalizes a raw CSS stylesheet string so that any explicit absolute font-size
     * declarations (e.g. `font-size: 14px;`, `font-size: 11pt !important;`) are wrapped
     * with `calc(... * var(--kreader-font-scale, 1))`.
     *
     * Relative units (`em`, `rem`, `%`), `calc(...)`, and elements without font-size (like `<hr>`)
     * are left untouched.
     */
    fun normalizeCssAbsoluteFontSizes(css: String): String {
        return FONT_SIZE_PROPERTY_REGEX.replace(css) { matchResult ->
            val value = matchResult.groupValues[1].trim()
            val important = matchResult.groupValues[2].trim()

            if (ABSOLUTE_FONT_SIZE_REGEX.matches(value)) {
                val replacement = "font-size: calc($value * var(--kreader-font-scale, 1))"
                if (important.isNotEmpty()) "$replacement $important" else replacement
            } else {
                matchResult.value
            }
        }
    }

    /**
     * Calculates the effective font size after applying a font scale factor.
     */
    fun computeScaledFontSize(originalValueWithUnit: String, fontScale: Float): String? {
        val match = ABSOLUTE_FONT_SIZE_REGEX.matchEntire(originalValueWithUnit.trim()) ?: return null
        val number = match.groupValues[1].toFloatOrNull() ?: return null
        val unit = match.groupValues[2].lowercase()
        val scaled = number * fontScale
        // Format to 2 decimal places, trimming trailing zeros
        val formatted = if (scaled % 1.0f == 0.0f) {
            "${scaled.toInt()}$unit"
        } else {
            val rounded = (scaled * 100).toInt() / 100.0f
            "$rounded$unit"
        }
        return formatted
    }

    /**
     * Builds the client-side JavaScript that traverses all document stylesheets and inline styles,
     * converting absolute font-size declarations to dynamic calc() rules governed by `--kreader-font-scale`.
     */
    fun buildNormalizationScript(fontScale: Float): String {
        return """
        (function() {
            var scale = $fontScale;
            if (document.documentElement) {
                document.documentElement.style.setProperty('--kreader-font-scale', scale);
            }

            var absRegex = /^([\d.]+)(px|pt|in|cm|mm|pc)$/i;

            function processRules(rules) {
                if (!rules) return;
                for (var i = 0; i < rules.length; i++) {
                    try {
                        var rule = rules[i];
                        if (rule.style && rule.style.fontSize) {
                            var fs = rule.style.fontSize.trim();
                            var match = fs.match(absRegex);
                            if (match) {
                                rule.style.setProperty(
                                    'font-size',
                                    'calc(' + fs + ' * var(--kreader-font-scale, 1))',
                                    rule.style.getPropertyPriority('font-size')
                                );
                            }
                        } else if (rule.cssRules) {
                            processRules(rule.cssRules);
                        }
                    } catch(e) {}
                }
            }

            try {
                for (var s = 0; s < document.styleSheets.length; s++) {
                    try {
                        var sheet = document.styleSheets[s];
                        if (sheet && sheet.cssRules) {
                            processRules(sheet.cssRules);
                        }
                    } catch(e) {}
                }
            } catch(e) {}

            try {
                var styledEls = document.querySelectorAll('[style*="font-size"]');
                for (var k = 0; k < styledEls.length; k++) {
                    var el = styledEls[k];
                    var inlineFs = el.style.fontSize;
                    if (inlineFs) {
                        var m = inlineFs.trim().match(absRegex);
                        if (m) {
                            el.style.setProperty(
                                'font-size',
                                'calc(' + inlineFs + ' * var(--kreader-font-scale, 1))',
                                el.style.getPropertyPriority('font-size')
                            );
                        }
                    }
                }
            } catch(e) {}
        })();
        """.trimIndent()
    }
}
