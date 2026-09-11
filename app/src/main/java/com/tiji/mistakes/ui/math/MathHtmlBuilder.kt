package com.tiji.mistakes.ui.math

import java.util.Locale
import org.json.JSONObject

internal fun buildMathHtml(
    source: String,
    maxLines: Int,
    textColor: Int,
    fontSizePx: Int,
    emphasized: Boolean,
    preserveSourceExactly: Boolean = false,
    allowDomLayout: Boolean = true,
    responsiveQuestionLayout: Boolean = false,
    compactVerticalSpacing: Boolean = false
): String {
    val dollar = '$'
    val quotedSource = JSONObject.quote(source)
    val color = String.format(Locale.US, "#%06X", textColor and 0xFFFFFF)
    val fontFamily = if (emphasized) "sans-serif" else "serif"
    val fontWeight = if (emphasized) 700 else 400
    val overflow = if (maxLines == Int.MAX_VALUE) "visible" else "hidden"
    val maxHeight = if (maxLines == Int.MAX_VALUE) {
        "none"
    } else {
        "${(maxLines * fontSizePx * 3 / 2).coerceAtLeast(fontSizePx + 6)}px"
    }
    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0" />
          <link rel="stylesheet" href="katex.min.css" />
          <style>
            html, body { margin: 0; padding: 0; background: transparent; }
            body { color: $color; font-family: $fontFamily; font-size: ${fontSizePx}px; font-weight: $fontWeight; line-height: 1.5; }
            #root { max-height: $maxHeight; overflow: $overflow; padding-bottom: .35em; box-sizing: border-box; white-space: pre-wrap; word-break: normal; overflow-wrap: break-word; line-break: strict; text-wrap: pretty; }
            #root.compact-vertical { padding-bottom: .12em; line-height: 1.40; }
            .prose { display: inline; white-space: pre-wrap; word-break: normal; overflow-wrap: break-word; line-break: strict; text-wrap: pretty; }
            .keep-unit { display: inline-flex; align-items: center; width: max-content; max-width: 100%; overflow-x: auto; overflow-y: hidden; white-space: nowrap; vertical-align: middle; -webkit-overflow-scrolling: touch; }
            .keep-line { display: block; width: max-content; max-width: 100%; overflow-x: auto; overflow-y: hidden; white-space: nowrap; margin: .10em 0; -webkit-overflow-scrolling: touch; }
            .keep-unit .prose, .keep-line .prose { white-space: pre; overflow-wrap: normal; }
            .keep-unit .inline-formula.long-formula, .keep-line .inline-formula.long-formula { display: inline-flex; width: auto; margin: 0; padding: .10em 0 .16em; overflow: visible; }
            .katex { font-size: 1.08em; }
            .inline-formula { display: inline-flex; align-items: center; max-width: 100%; padding: .04em 0 .08em; white-space: nowrap; vertical-align: middle; line-height: 1.16; }
            .inline-formula.long-formula { display: inline-flex; width: auto; max-width: 100%; overflow-x: auto; overflow-y: hidden; margin: 0; padding: .04em 0 .08em; box-sizing: border-box; text-align: left; line-height: 1.16; vertical-align: middle; white-space: nowrap; -webkit-overflow-scrolling: touch; }
            .inline-formula.long-formula .katex { display: inline-block; margin: 0; vertical-align: middle; line-height: 1.16; text-align: left !important; }
            .inline-formula.long-formula.has-tail { display: inline-flex; width: max-content; max-width: 100%; overflow-x: auto; overflow-y: hidden; white-space: nowrap; vertical-align: middle; -webkit-overflow-scrolling: touch; }
            .inline-formula.long-formula.has-tail .katex { display: inline-block; }
            .inline-formula.responsive-block { display: inline-flex; width: max-content; max-width: 100%; overflow-x: auto; overflow-y: visible; margin: 0; padding: .04em 0 .08em; box-sizing: border-box; align-items: center; white-space: nowrap; vertical-align: middle; -webkit-overflow-scrolling: touch; }
            .inline-formula.responsive-block .katex { display: inline-block; max-width: none; margin: 0; vertical-align: middle; }
            .display-formula { display: block; max-width: 100%; margin: 0; padding: .02em 0 .04em; box-sizing: border-box; overflow-x: auto; overflow-y: visible; text-align: left !important; white-space: nowrap; line-height: 1.20; -webkit-overflow-scrolling: touch; }
            .display-formula .katex-display { display: block; margin: 0; padding: 0; line-height: 1.20; text-align: left !important; }
            .display-formula .katex-display > .katex { display: block; margin-left: 0; margin-right: 0; text-align: left !important; }
            #root.compact-vertical .display-formula { margin: .18em 0 .20em; padding: 0; line-height: 1.18; }
            #root.compact-vertical .display-formula .katex-display { line-height: 1.18; }
             .display-formula.aligned-formula, .display-formula.structured-formula { width: 100%; overflow-x: auto; overflow-y: visible; text-align: left; white-space: normal; -webkit-overflow-scrolling: touch; }
             .display-formula.aligned-formula .katex-display, .display-formula.structured-formula .katex-display { margin: 0; text-align: left; }
             .display-formula.structured-formula .katex-display > .katex { max-width: none; }
            .display-formula.has-tail { display: inline-block; width: max-content; max-width: 100%; overflow-x: auto; overflow-y: hidden; white-space: nowrap; vertical-align: middle; text-align: left; }
            .display-formula.has-tail .katex-display { display: inline; margin: 0; }
            .display-formula.has-tail .katex-display > .katex { display: inline-block; }
            .formula-tail { display: inline; white-space: nowrap; }
            .inline-formula.fraction-formula { padding: .02em 0 .04em; }
            .inline-formula .katex { display: inline-flex; align-items: center; vertical-align: middle; line-height: 1; }
            .mfrac.primary-fraction > .vlist-t > .vlist-r > .vlist > span:nth-child(1) { transform: translateY(-.03em); }
            .mfrac.primary-fraction > .vlist-t > .vlist-r > .vlist > span:nth-child(3) { transform: translateY(.12em); }
            .integral-operator { display: inline-block; transform: scaleY(.90); transform-origin: center 55%; }
            .katex .fbox { border: 0 !important; padding: 0 !important; }
            .fallback { white-space: pre-wrap; }
          </style>
        </head>
        <body>
            <div id="root" class="${if (compactVerticalSpacing) "compact-vertical" else ""}"></div>
          <script src="katex.min.js"></script>
          <script>
            (() => {
              const preserveRawSource = ${if (preserveSourceExactly) "true" else "false"};
              const allowDomLayout = ${if (allowDomLayout) "true" else "false"};
              const responsiveQuestionLayout = ${if (responsiveQuestionLayout) "true" else "false"};
              const compactVerticalSpacing = ${if (compactVerticalSpacing) "true" else "false"};
              // Decode only a literal newline escape at render time. The
              // look-ahead deliberately excludes LaTeX commands such as \\ne.
              const source = $quotedSource
                .replace(/\\n(?![A-Za-z])/g, '\n');
              const root = document.getElementById('root');
              let renderTarget = root;
              let preserveLeadingLineBreaks = false;
              let lastFormulaNode = null;
              const pattern = /(\\\[([\s\S]*?)\\\]|\\\(([\s\S]*?)\\\)|\${dollar}\${dollar}([\s\S]*?)\${dollar}\${dollar}|\${dollar}(?!\${dollar})([\s\S]*?)\${dollar})/g;

              function appendText(text) {
                if (!text) return;
                // Layout-only cleanup: keep the model response unchanged, but
                // do not render accidental empty paragraphs as blank lines.
                if (allowDomLayout && (!preserveRawSource || compactVerticalSpacing)) {
                  text = text
                    .replace(/\r\n?/g, '\n')
                    .replace(/[ \t\u00a0]*\n(?:[ \t\u00a0]*\n)+/g, '\n');
                }
                if (!text) return;
                const prose = document.createElement('span');
                prose.className = 'prose';
                prose.appendChild(document.createTextNode(text));
                renderTarget.appendChild(prose);
              }

              function isDisplayFormulaToken(token) {
                return token && (token.startsWith('$$') || token.startsWith('\\['));
              }

              function isStructuredFormulaSource(value) {
                return /\\begin\s*\{(?:aligned|alignedat|array|gathered|gather|multline|cases|dcases|rcases|matrix|pmatrix|bmatrix|Bmatrix|vmatrix|Vmatrix|smallmatrix)\}/.test(value || '');
              }

              function appendBetween(text, afterFormula, beforeFormula, adjacentFormulaToken = '') {
                if (allowDomLayout && !responsiveQuestionLayout && beforeFormula && isDisplayFormulaToken(adjacentFormulaToken) && (compactVerticalSpacing || !isStructuredFormulaSource(adjacentFormulaToken))) {
                  text = text.replace(/(?:[ \t]*(?:\r?\n|\\n)[ \t]*)+$/, '');
                }
                if (allowDomLayout && afterFormula && lastFormulaNode) {
                  if (lastFormulaNode.classList.contains('display-formula')) {
                    text = text.replace(/^(?:[ \t]*(?:\r?\n|\\n)[ \t]*)+/, '');
                  }
                  const punctuation = text.match(/^([\uFF0C\u3002\uFF01\uFF1F\uFF1B\uFF1A\u3001,.!?;:]+)/);
                  if (punctuation) {
                    const tail = document.createElement('span');
                    tail.className = 'formula-tail';
                    tail.textContent = punctuation[1];
                    lastFormulaNode.classList.add('has-tail');
                    lastFormulaNode.appendChild(tail);
                    text = text.slice(punctuation[0].length);
                  }
                }
                appendText(text);
              }

              function appendFormula(token, bare = false) {
                let formula = token;
                let display = false;
                if (!bare) {
                  if (token.startsWith('${dollar}${dollar}')) { formula = token.slice(2, -2); display = true; }
                  else if (token.startsWith('${dollar}')) formula = token.slice(1, -1);
                  else { formula = token.slice(2, -2); display = token.startsWith('\\['); }
                 }
                 const structured = isStructuredFormulaSource(formula);
                 if (structured) {
                   const rows = formula.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
                   formula = rows.map((line, index) => {
                     const nextLine = rows[index + 1] || '';
                     const pureEnvironmentBoundary = /^\\(?:begin|end)\s*\{[^}]+\}\s*$/.test(line);
                     const nextIsEnvironmentEnd = /^\\end\s*\{[^}]+\}\s*$/.test(nextLine);
                     if (pureEnvironmentBoundary || nextIsEnvironmentEnd || index === rows.length - 1 || /\\\\(?:\s*\[[^\]]*])?\s*$/.test(line)) return line;
                     return line + ' \\\\';
                   }).join('\n');
                 } else {
                   formula = formula.replace(/\\\\/g, ' ');
                 }
                formula = formula.replace(/\r?\n/g, ' ').replace(/\s{2,}/g, ' ').trim();
                try {
                  const node = document.createElement('span');
                  const responsiveBlock = display && responsiveQuestionLayout;
                  const renderDisplay = display && !responsiveBlock;
                  node.className = (renderDisplay ? 'display-formula' : 'inline-formula') + (structured ? ' structured-formula' : '') + (responsiveBlock ? ' responsive-block' : '');
                  node.dataset.formula = formula;
                  const renderedFormula = '\\displaystyle ' + formula;
                  node.innerHTML = katex.renderToString(renderedFormula, {
                    displayMode: renderDisplay,
                    throwOnError: true,
                    output: 'htmlAndMathml'
                  });
                  renderTarget.appendChild(node);
                  lastFormulaNode = node;
                } catch (error) {
                  appendText(token);
                }
              }

              function decorateFormulas() {
                document.querySelectorAll('.inline-formula, .display-formula').forEach((wrapper) => {
                  wrapper.querySelectorAll('.mfrac').forEach((fraction) => {
                    const nestedFraction = fraction.parentElement?.closest('.mfrac');
                    if (!nestedFraction && !fraction.closest('.msupsub')) fraction.classList.add('primary-fraction');
                  });
                  wrapper.querySelectorAll('.op-symbol.large-op').forEach((symbol) => {
                    if (/^[∫∬∭∮∯∰]+$/.test(symbol.textContent.trim())) symbol.parentElement?.classList.add('integral-operator');
                  });
                  if (wrapper.querySelector('.mfrac.primary-fraction')) wrapper.classList.add('fraction-formula');
                  const math = wrapper.querySelector('.katex');
                  if (math) {
                    const overflowing = math.scrollWidth > root.clientWidth - 4;
                    if (overflowing && wrapper.classList.contains('inline-formula')) {
                      wrapper.classList.add('long-formula');
                    }
                  }
                });
              }

              function collectMatches(regex, value) {
                const matches = [];
                let match;
                regex.lastIndex = 0;
                while ((match = regex.exec(value)) !== null) {
                  if (match[0].length === 0) {
                    regex.lastIndex += 1;
                    continue;
                  }
                  matches.push(match);
                }
                return matches;
              }

              function renderDelimited(value) {
                const matches = collectMatches(pattern, value);
                if (matches.length === 0) return false;
                let cursor = 0;
                let afterFormula = false;
                for (const match of matches) {
                  appendBetween(value.slice(cursor, match.index), afterFormula, true, match[0]);
                  appendFormula(match[0]);
                  cursor = match.index + match[0].length;
                  afterFormula = true;
                }
                appendBetween(value.slice(cursor), afterFormula, false);
                return true;
              }

              function renderBareLatex(value) {
                const barePattern = /(\\(?:d?frac|tfrac|sqrt|sum|prod|int|lim|sin|cos|tan|ln|log|alpha|beta|gamma|delta|theta|lambda|mu|pi|sigma|phi|Delta|Omega|leq|geq|neq|times|cdot)(?:\s*(?:\{[^{}]*\}|[A-Za-z0-9]+)){1,3})/g;
                const matches = collectMatches(barePattern, value);
                if (matches.length === 0) return false;
                let cursor = 0;
                let afterFormula = false;
                for (const match of matches) {
                  appendBetween(value.slice(cursor, match.index), afterFormula, true, match[0]);
                  appendFormula(match[0], true);
                  cursor = match.index + match[0].length;
                  afterFormula = true;
                }
                appendBetween(value.slice(cursor), afterFormula, false);
                return true;
              }

              function renderSegment(value, preserveLeading = false) {
                if (!value) return;
                preserveLeadingLineBreaks = preserveLeading;
                lastFormulaNode = null;
                if (!renderDelimited(value) && !renderBareLatex(value)) {
                  const hasChinese = /[\u4e00-\u9fff]/.test(value);
                  const looksLikeFormula =
                     /\\(?:frac|dfrac|tfrac|sqrt|sum|prod|int|lim|left|right|begin|end)\b/.test(value) ||
                    (/[^\u4e00-\u9fff]/.test(value) && /[\^_]/.test(value));
                  if (looksLikeFormula) {
                    appendFormula(value, true);
                  } else {
                    appendText(value);
                  }
                }
                preserveLeadingLineBreaks = false;
              }

              renderTarget = root;
              renderSegment(source, true);
              renderTarget = root;

              requestAnimationFrame(() => {
                decorateFormulas();
                requestAnimationFrame(decorateFormulas);
              });
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}
