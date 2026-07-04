package com.neomechanical.neomoderation.moderation;

import org.bukkit.Bukkit;
import org.bukkit.map.MapPalette;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

public final class MapArtScanner {
    private MapArtScanner() {
    }

    public static String getBase64Image(int mapId) {
        try {
            if (Bukkit.getWorlds().isEmpty()) {
                return null;
            }
            File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
            File dataFolder = new File(worldFolder, "data");
            File mapFile = new File(dataFolder, "map_" + mapId + ".dat");
            if (!mapFile.exists()) {
                return null;
            }

            byte[] colors = extractColorsFromNbt(mapFile);
            if (colors == null || colors.length < 16384) {
                return null;
            }

            BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int index = y * 128 + x;
                    @SuppressWarnings("deprecation")
                    Color color = MapPalette.getColor(colors[index]);
                    image.setRGB(x, y, color.getRGB());
                }
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] extractColorsFromNbt(File file) {
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(new FileInputStream(file)))) {
            while (true) {
                int tag = input.readByte();
                if (tag == 0) {
                    continue;
                }
                int nameLength = input.readUnsignedShort();
                byte[] nameBytes = new byte[nameLength];
                input.readFully(nameBytes);
                String name = new String(nameBytes);
                if (tag == 7 && "colors".equals(name)) {
                    int length = input.readInt();
                    byte[] payload = new byte[length];
                    input.readFully(payload);
                    return payload;
                }
                skipTagPayload(input, tag);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void skipTagPayload(DataInputStream input, int tag) throws Exception {
        switch (tag) {
            case 1 -> input.skipBytes(1);
            case 2 -> input.skipBytes(2);
            case 3 -> input.skipBytes(4);
            case 4 -> input.skipBytes(8);
            case 5 -> input.skipBytes(4);
            case 6 -> input.skipBytes(8);
            case 7 -> {
                int length = input.readInt();
                input.skipBytes(length);
            }
            case 8 -> {
                int length = input.readUnsignedShort();
                input.skipBytes(length);
            }
            case 9 -> {
                int listType = input.readByte();
                int length = input.readInt();
                for (int i = 0; i < length; i++) {
                    skipTagPayload(input, listType);
                }
            }
            case 10 -> {
                while (true) {
                    int subTag = input.readByte();
                    if (subTag == 0) {
                        break;
                    }
                    int nameLength = input.readUnsignedShort();
                    input.skipBytes(nameLength);
                    skipTagPayload(input, subTag);
                }
            }
            case 11 -> {
                int length = input.readInt();
                input.skipBytes(length * 4);
            }
            case 12 -> {
                int length = input.readInt();
                input.skipBytes(length * 8);
            }
            default -> {
            }
        }
    }
}
