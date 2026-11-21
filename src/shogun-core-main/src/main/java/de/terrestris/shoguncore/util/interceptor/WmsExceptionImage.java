package de.terrestris.shoguncore.util.interceptor;

import org.apache.commons.lang3.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a WMS GetMap exception as an image, for EXCEPTIONS=INIMAGE and
 * EXCEPTIONS=BLANK (WMS 1.3.0, 7.3.3.11).
 */
final class WmsExceptionImage {

    private static final int MAX_SIZE = 4096;

    private static final int DEFAULT_SIZE = 256;

    private static final int RGB_MASK = 0xFFFFFF;

    private static final int TEXT_MARGIN = 4;

    private static final int LINE_HEIGHT = 14;

    private WmsExceptionImage() {
    }

    /**
     * @param params The request parameters (upper case keys)
     * @return true if the client asked for an image exception
     */
    static boolean isRequested(Map<String, String> params) {
        if (!"GetMap".equalsIgnoreCase(params.get("REQUEST"))) {
            return false;
        }
        return mode(params) != null && imageFormat(params) != null;
    }

    /**
     * @return "INIMAGE", "BLANK" or null
     */
    private static String mode(Map<String, String> params) {
        String exceptions = StringUtils.defaultString(params.get("EXCEPTIONS")).toUpperCase(Locale.ROOT);
        if (exceptions.equals("INIMAGE") || exceptions.endsWith("SE_INIMAGE")) {
            return "INIMAGE";
        }
        if (exceptions.equals("BLANK") || exceptions.endsWith("SE_BLANK")) {
            return "BLANK";
        }
        return null;
    }

    /**
     * @return The ImageIO format name for the FORMAT parameter, or null
     */
    private static String imageFormat(Map<String, String> params) {
        String format = StringUtils.defaultString(params.get("FORMAT")).toLowerCase(Locale.ROOT);
        format = format.split(";")[0].trim();
        switch (format) {
            case "image/png":
            case "image/png8":
                return "png";
            case "image/jpeg":
                return "jpeg";
            case "image/gif":
                return "gif";
            default:
                return null;
        }
    }

    /**
     * @return The media type of the image (the FORMAT parameter)
     */
    static String mediaType(Map<String, String> params) {
        return params.get("FORMAT");
    }

    static byte[] render(Map<String, String> params, String message) throws IOException {
        String format = imageFormat(params);
        int width = size(params.get("WIDTH"));
        int height = size(params.get("HEIGHT"));
        boolean transparent = "TRUE".equalsIgnoreCase(params.get("TRANSPARENT")) && !"jpeg".equals(format);
        Color background = Color.WHITE;
        String bgcolor = params.get("BGCOLOR");
        if (StringUtils.isNotEmpty(bgcolor)) {
            try {
                background = new Color(Integer.decode(bgcolor.trim()) & RGB_MASK);
            } catch (NumberFormatException e) {
                // keep the default
            }
        }

        BufferedImage image = new BufferedImage(width, height,
            transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            if (!transparent) {
                g.setColor(background);
                g.fillRect(0, 0, width, height);
            }
            if ("INIMAGE".equals(mode(params))) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
                g.setColor(Color.BLACK);
                g.drawString(StringUtils.defaultString(message), TEXT_MARGIN, LINE_HEIGHT);
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static int size(String value) {
        try {
            int size = Integer.parseInt(StringUtils.trim(value));
            return size > 0 && size <= MAX_SIZE ? size : DEFAULT_SIZE;
        } catch (NumberFormatException e) {
            return DEFAULT_SIZE;
        }
    }
}
