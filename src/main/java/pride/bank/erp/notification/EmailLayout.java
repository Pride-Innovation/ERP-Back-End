/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.notification;

import java.time.Year;
import java.util.List;

/**
 * The one shell every email from this application is rendered in.
 *
 * <p>There were seven templates, each built by hand in {@link Email}, and they had drifted into seven
 * looks: four card widths (620, 650, 700, 900px), headers from 36 to 50px of padding, pill and square
 * buttons, emoji icons, three different "automated message" boxes naming three different systems,
 * and a footer stamped "© 2025". This class is now the only place the page furniture exists - a
 * template supplies a title and a body, and cannot restyle the frame around it.
 *
 * <p><b>Built for mail clients, not browsers.</b> Layout is tables and every style is inline. The
 * old templates leaned on things the major clients drop: {@code linear-gradient} (Outlook), {@code
 * display:flex} / {@code grid} (Outlook, several webmail), and {@code filter: invert(1)} on the logo
 * (Gmail and Outlook both strip it) - which left the full-colour logo sitting on a teal band it was
 * never designed for. The logo now sits on white, as it is drawn.
 *
 * <p>Every value that came from a person - a name, a comment, a leave reason - goes through
 * {@link #esc}. The templates used to interpolate them raw into HTML.
 */
public final class EmailLayout {

    private EmailLayout() {
    }

    // ── Palette ─────────────────────────────────────────────────────────────────────────────────
    static final String BRAND = "#05544B";
    static final String GOLD = "#BC892C";
    private static final String PAGE_BG = "#F4F6F8";
    private static final String BORDER = "#E5E9EE";
    private static final String RULE = "#EEF1F4";
    private static final String INK = "#1F2933";
    private static final String TEXT = "#3E4C59";
    private static final String MUTED = "#7B8794";
    /*
     * Unquoted on purpose. Every style attribute here is single-quoted, so a quoted family name
     * ('Segoe UI') closed the attribute early and silently dropped the rest of the styling - the
     * first render came out in the client's default serif. Multi-word family names are valid CSS
     * without quotes.
     */
    private static final String FONT = "Segoe UI,Roboto,Helvetica,Arial,sans-serif";

    /** What kind of message this is - colours the label above the title, and nothing else. */
    public enum Tone {
        ACTION("#FBF3E4", "#8A6219"),
        INFO("#E6F2F0", BRAND),
        SUCCESS("#E7F5EC", "#1E7B45"),
        DANGER("#FDECEC", "#B42318");

        final String background;
        final String foreground;

        Tone(String background, String foreground) {
            this.background = background;
            this.foreground = foreground;
        }
    }

    /**
     * A complete email.
     *
     * @param title       the headline, plain text
     * @param tone        colours the label
     * @param label       short label above the title ("Action required"), plain text
     * @param preheader   the line inbox lists show beside the subject, plain text
     * @param bodyHtml    the content, built from the helpers below
     * @param actionLabel button text, or null for no button
     * @param actionUrl   button target, or null for no button
     */
    public static String page(String title, Tone tone, String label, String preheader,
                              String bodyHtml, String actionLabel, String actionUrl) {
        boolean hasAction = actionLabel != null && actionUrl != null && !actionUrl.isBlank();

        return "<!DOCTYPE html><html lang='en'><head>"
                + "<meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                + "<meta name='x-apple-disable-message-reformatting'>"
                + "<title>" + esc(title) + "</title></head>"
                + "<body style='margin:0;padding:0;background-color:" + PAGE_BG + ";'>"

                // Inbox preview text, hidden in the message itself.
                + "<div style='display:none;max-height:0;overflow:hidden;mso-hide:all;font-size:1px;"
                + "line-height:1px;color:" + PAGE_BG + ";'>" + esc(preheader == null ? title : preheader) + "</div>"

                + "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' "
                + "style='background-color:" + PAGE_BG + ";'><tr><td align='center' style='padding:32px 12px;'>"

                + "<table role='presentation' width='600' cellpadding='0' cellspacing='0' border='0' "
                + "style='width:100%;max-width:600px;background-color:#FFFFFF;border:1px solid " + BORDER + ";"
                + "border-radius:8px;border-collapse:separate;font-family:" + FONT + ";'>"

                // Brand bar
                + "<tr><td style='height:4px;line-height:4px;font-size:0;background-color:" + BRAND + ";"
                + "border-radius:8px 8px 0 0;'>&nbsp;</td></tr>"

                // Masthead: logo left, application name right
                + "<tr><td style='padding:22px 32px 18px;border-bottom:1px solid " + RULE + ";'>"
                + "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0'><tr>"
                + "<td valign='middle'><img src='cid:logoImage' width='150' alt='Pride Bank' "
                + "style='display:block;width:150px;height:auto;border:0;outline:none;' /></td>"
                + "<td valign='middle' align='right' style='font-family:" + FONT + ";'>"
                + "<span style='font-size:11px;line-height:16px;font-weight:700;letter-spacing:1.6px;"
                + "text-transform:uppercase;color:" + BRAND + ";'>Assets Management</span><br/>"
                + "<span style='font-size:11px;line-height:16px;color:" + MUTED + ";'>Pride Bank ERP</span>"
                + "</td></tr></table></td></tr>"

                // Label + title
                + "<tr><td style='padding:28px 32px 4px;'>"
                + label(tone, label)
                + "<h1 style='margin:12px 0 0;font-family:" + FONT + ";font-size:20px;line-height:28px;"
                + "font-weight:700;color:" + INK + ";'>" + esc(title) + "</h1>"
                + "</td></tr>"

                // Body
                + "<tr><td style='padding:18px 32px " + (hasAction ? "4px" : "28px") + ";font-family:" + FONT + ";"
                + "font-size:14px;line-height:22px;color:" + TEXT + ";'>" + bodyHtml + "</td></tr>"

                + (hasAction ? action(actionLabel, actionUrl) : "")

                // Footer
                + "<tr><td style='padding:20px 32px;background-color:#F9FAFB;border-top:1px solid " + RULE + ";"
                + "border-radius:0 0 8px 8px;font-family:" + FONT + ";font-size:12px;line-height:18px;color:" + MUTED + ";'>"
                + "<strong style='color:#52606D;'>Pride Bank Limited &middot; Assets Management</strong><br/>"
                + "Plot 6-9, Victoria Office Park, Ben Kiwanuka Okot Close, Bukoto, Kampala &middot; +256 754 346930<br/>"
                + "This is an automated message from the Assets Management system. Please do not reply to this email."
                + "</td></tr>"

                + "</table>"

                + "<table role='presentation' width='600' cellpadding='0' cellspacing='0' border='0' style='width:100%;max-width:600px;'>"
                + "<tr><td align='center' style='padding:16px 12px 0;font-family:" + FONT + ";font-size:11px;"
                + "line-height:16px;color:#9AA5B1;'>&copy; " + Year.now().getValue()
                + " Pride Bank Limited. All rights reserved.</td></tr></table>"

                + "</td></tr></table></body></html>";
    }

    // ── Body building blocks ────────────────────────────────────────────────────────────────────

    /** A paragraph. {@code html} is trusted markup - escape any value inside it first. */
    public static String paragraph(String html) {
        return "<p style='margin:0 0 14px;'>" + html + "</p>";
    }

    /** "Dear Jane," - or "Hello," when there is nobody to name. */
    public static String greeting(String name) {
        return (name == null || name.isBlank())
                ? paragraph("Hello,")
                : paragraph("Dear <strong style='color:" + INK + ";'>" + esc(name.trim()) + "</strong>,");
    }

    /** Emphasised inline value inside a paragraph, e.g. a request number. */
    public static String strong(String text) {
        return "<strong style='color:" + INK + ";'>" + esc(text) + "</strong>";
    }

    /**
     * A label/value list under a small heading. Rows whose value is blank are left out, so a template
     * can list everything it might know without printing empty lines.
     */
    public static String details(String heading, List<String[]> rows) {
        StringBuilder body = new StringBuilder();
        boolean first = true;
        for (String[] row : rows) {
            if (row == null || row.length < 2 || row[1] == null || row[1].isBlank()) continue;
            body.append("<tr><td style='padding:9px 14px;width:38%;vertical-align:top;font-size:12px;line-height:18px;"
                            + "color:" + MUTED + ";").append(first ? "" : "border-top:1px solid " + RULE + ";")
                    .append("'>").append(esc(row[0])).append("</td>")
                    .append("<td style='padding:9px 14px;vertical-align:top;font-size:14px;line-height:20px;"
                            + "font-weight:600;color:" + INK + ";").append(first ? "" : "border-top:1px solid " + RULE + ";")
                    .append("'>").append(esc(row[1])).append("</td></tr>");
            first = false;
        }
        if (body.length() == 0) return "";
        return sectionHeading(heading)
                + "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' "
                + "style='border:1px solid " + BORDER + ";border-radius:6px;border-collapse:separate;margin:0 0 18px;'>"
                + body + "</table>";
    }

    /**
     * A table of rows - the requested or stocked items. Columns after the first are right-aligned
     * when {@code numericFrom} says so, so quantities line up.
     *
     * @param footer optional closing row (e.g. a total), same column count, or null
     */
    public static String table(String heading, String[] columns, List<String[]> rows, int numericFrom, String[] footer) {
        if (rows == null || rows.isEmpty()) return "";
        StringBuilder html = new StringBuilder(sectionHeading(heading));
        html.append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' "
                + "style='border:1px solid " + BORDER + ";border-radius:6px;border-collapse:separate;margin:0 0 18px;'>");

        html.append("<tr>");
        for (int i = 0; i < columns.length; i++) {
            html.append("<th align='").append(i >= numericFrom ? "right" : "left")
                    .append("' style='padding:9px 14px;background-color:#F9FAFB;border-bottom:1px solid ").append(BORDER)
                    .append(";font-size:11px;line-height:16px;font-weight:700;letter-spacing:0.6px;"
                            + "text-transform:uppercase;color:").append(MUTED).append(";'>")
                    .append(esc(columns[i])).append("</th>");
        }
        html.append("</tr>");

        boolean first = true;
        for (String[] row : rows) {
            html.append("<tr>");
            for (int i = 0; i < columns.length; i++) {
                html.append("<td align='").append(i >= numericFrom ? "right" : "left")
                        .append("' style='padding:9px 14px;font-size:14px;line-height:20px;color:").append(INK).append(";")
                        .append(first ? "" : "border-top:1px solid " + RULE + ";").append("'>")
                        .append(esc(i < row.length ? row[i] : "")).append("</td>");
            }
            html.append("</tr>");
            first = false;
        }

        if (footer != null) {
            html.append("<tr>");
            for (int i = 0; i < columns.length; i++) {
                html.append("<td align='").append(i >= numericFrom ? "right" : "left")
                        .append("' style='padding:10px 14px;background-color:#F9FAFB;border-top:1px solid ").append(BORDER)
                        .append(";font-size:13px;line-height:20px;font-weight:700;color:").append(INK).append(";'>")
                        .append(esc(i < footer.length ? footer[i] : "")).append("</td>");
            }
            html.append("</tr>");
        }
        return html.append("</table>").toString();
    }

    /** A highlighted note - a comment, a reason, a warning. {@code html} is trusted markup. */
    public static String callout(String heading, String html, Tone tone) {
        return "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='margin:0 0 18px;'>"
                + "<tr><td style='padding:12px 16px;background-color:" + tone.background + ";"
                + "border-left:3px solid " + tone.foreground + ";border-radius:0 6px 6px 0;'>"
                + (heading == null ? "" : "<p style='margin:0 0 4px;font-size:12px;line-height:18px;font-weight:700;"
                + "color:" + tone.foreground + ";'>" + esc(heading) + "</p>")
                + "<p style='margin:0;font-size:14px;line-height:21px;color:" + INK + ";'>" + html + "</p>"
                + "</td></tr></table>";
    }

    /** A value to be copied exactly, such as a temporary password. */
    public static String codeBox(String heading, String value, String note) {
        return sectionHeading(heading)
                + "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' border='0' style='margin:0 0 18px;'>"
                + "<tr><td align='center' style='padding:16px;background-color:#F9FAFB;border:1px dashed #C4CDD5;border-radius:6px;'>"
                + "<span style='font-family:Consolas,Courier New,monospace;font-size:20px;line-height:28px;"
                + "font-weight:700;letter-spacing:3px;color:" + INK + ";'>" + esc(value) + "</span>"
                + (note == null ? "" : "<br/><span style='font-size:12px;line-height:18px;color:" + MUTED + ";'>" + esc(note) + "</span>")
                + "</td></tr></table>";
    }

    /** Escapes text for HTML. Null becomes an empty string. */
    public static String esc(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    // ── Internals ───────────────────────────────────────────────────────────────────────────────

    private static String label(Tone tone, String text) {
        if (text == null || text.isBlank()) return "";
        return "<span style='display:inline-block;padding:3px 10px;border-radius:4px;background-color:" + tone.background
                + ";font-family:" + FONT + ";font-size:11px;line-height:16px;font-weight:700;letter-spacing:0.8px;"
                + "text-transform:uppercase;color:" + tone.foreground + ";'>" + esc(text) + "</span>";
    }

    private static String sectionHeading(String heading) {
        if (heading == null) return "";
        return "<p style='margin:6px 0 8px;font-size:11px;line-height:16px;font-weight:700;letter-spacing:0.8px;"
                + "text-transform:uppercase;color:" + MUTED + ";'>" + esc(heading) + "</p>";
    }

    /** A button that survives Outlook: a padded table cell, not a styled link alone. */
    private static String action(String label, String url) {
        String href = esc(url);
        return "<tr><td style='padding:14px 32px 28px;font-family:" + FONT + ";'>"
                + "<table role='presentation' cellpadding='0' cellspacing='0' border='0'><tr>"
                + "<td style='border-radius:6px;background-color:" + BRAND + ";'>"
                + "<a href='" + href + "' style='display:inline-block;padding:11px 24px;font-size:14px;line-height:20px;"
                + "font-weight:600;color:#FFFFFF;text-decoration:none;border-radius:6px;'>" + esc(label) + "</a>"
                + "</td></tr></table>"
                + "<p style='margin:14px 0 0;font-size:12px;line-height:18px;color:" + MUTED + ";'>"
                + "If the button does not work, copy this link into your browser:<br/>"
                + "<a href='" + href + "' style='color:" + BRAND + ";word-break:break-all;'>" + href + "</a></p>"
                + "</td></tr>";
    }
}
