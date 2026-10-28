package com.flo.nodefaultbrowser;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal QR Code encoder (ISO 18004): byte mode only, error correction level M.
 * Kept deliberately small - enough for URLs up to 2331 characters, no dependencies.
 * Returns the module matrix (true = dark) or null when the text does not fit.
 */
class QrCode {

    // ISO 18004 Tables 13/15 for level M, index = version (1..40)
    private static final int[] ECC = { 0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28 };
    private static final int[] BLOCKS = { 0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49 };
    private static final int[] TOTALS = { 26, 44, 70, 100, 134, 172, 196, 242, 292, 346, 404, 466, 532, 581, 655, 733, 815, 901, 991, 1085, 1156, 1258, 1364, 1474, 1588, 1706, 1828, 1921, 2051, 2185, 2323, 2465, 2611, 2761, 2876, 3034, 3196, 3362, 3532, 3706 };

    // ISO 18004 Annex E: alignment pattern centers per version (2..40).
    private static final int[][] ALIGNMENT = {
        {6, 18}, {6, 22}, {6, 26}, {6, 30}, {6, 34}, {6, 22, 38}, {6, 24, 42}, {6, 26, 46}, {6, 28, 50}, {6, 30, 54},
        {6, 32, 58}, {6, 34, 62}, {6, 26, 46, 66}, {6, 26, 48, 70}, {6, 26, 50, 74}, {6, 30, 54, 78}, {6, 30, 56, 82},
        {6, 30, 58, 86}, {6, 34, 62, 90}, {6, 28, 50, 72, 94}, {6, 26, 50, 74, 98}, {6, 30, 54, 78, 102},
        {6, 28, 54, 80, 106}, {6, 32, 58, 84, 110}, {6, 30, 58, 86, 114}, {6, 34, 62, 90, 118},
        {6, 26, 50, 74, 98, 122}, {6, 30, 54, 78, 102, 126}, {6, 26, 52, 78, 104, 130}, {6, 30, 56, 82, 108, 134},
        {6, 34, 60, 86, 112, 138}, {6, 30, 58, 86, 114, 142}, {6, 34, 62, 90, 118, 146},
        {6, 30, 54, 78, 102, 126, 150}, {6, 24, 50, 76, 102, 128, 154}, {6, 28, 54, 80, 106, 132, 158},
        {6, 32, 58, 84, 110, 136, 162}, {6, 26, 54, 82, 110, 138, 166}, {6, 30, 58, 86, 114, 142, 170},
    };

    private final int size;
    private final boolean[][] dark;
    private final boolean[][] function;
    private final List<Integer> bits = new ArrayList<>();

    static boolean[][] encode(String text) {
        byte[] payload;
        try {
            payload = text.getBytes("UTF-8");
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
        boolean eci = false;
        for (byte b : payload) if (b < 0) { eci = true; break; }
        int version = 0;
        for (int v = 1; v <= 40; v++) if ((eci ? 12 : 0) + 4 + (v < 10 ? 8 : 16) + 8 * payload.length <= 8 * (TOTALS[v - 1] - ECC[v] * BLOCKS[v])) { version = v; break; }
        if (version == 0) return null;
        return new QrCode(version, payload, eci).build();
    }

    private QrCode(int version, byte[] payload, boolean eci) {
        size = 17 + 4 * version;
        dark = new boolean[size][size];
        function = new boolean[size][size];
        drawFunctionPatterns(version);
        if (eci) { appendBits(7, 4); appendBits(26, 8); }
        appendBits(4, 4);
        appendBits(payload.length, version < 10 ? 8 : 16);
        for (byte b : payload) appendBits(b & 0xFF, 8);
        int capacity = 8 * (TOTALS[version - 1] - ECC[version] * BLOCKS[version]);
        appendBits(0, Math.min(4, capacity - bits.size()));
        while (bits.size() % 8 != 0) bits.add(0);
        for (int pad = 0xEC; bits.size() < capacity; pad ^= 0xEC ^ 0x11) appendBits(pad, 8);
        interleave(version, capacity);
    }

    private boolean[][] build() {
        int best = 0, bestPenalty = Integer.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            boolean[][] trial = masked(mask);
            paintFormat(trial, mask);
            int penalty = penalty(trial);
            if (penalty < bestPenalty) { bestPenalty = penalty; best = mask; }
        }
        paintFormat(dark, best);
        for (int row = 0; row < size; row++) for (int col = 0; col < size; col++) if (!function[row][col]) dark[row][col] ^= maskHit(best, row, col);
        return dark;
    }

    private void appendBits(int value, int count) {
        for (int i = count - 1; i >= 0; i--) bits.add(value >>> i & 1);
    }

    /**
     * Finder patterns with separators, timing lines, alignment patterns,
     * the reserved format areas and the version areas of big symbols.
     */
    private void drawFunctionPatterns(int version) {
        for (int i = 0; i < size; i++) { set(i, 6, i % 2 == 0); set(6, i, i % 2 == 0); }
        finder(3, 3); finder(size - 4, 3); finder(3, size - 4);
        if (version > 1) {
            int[] positions = ALIGNMENT[version - 2];
            for (int y : positions) for (int x : positions) {
                boolean corner = (x == 6 && y == 6) || (x == 6 && y == size - 7) || (x == size - 7 && y == 6);
                if (!corner) alignment(x, y);
            }
        }
        for (int i = 0; i < 9; i++) { reserve(8, i); reserve(i, 8); }
        for (int i = 0; i < 8; i++) reserve(size - 1 - i, 8);
        for (int i = 8; i < 15; i++) reserve(8, size - 15 + i);
        reserve(8, size - 8);
        if (version >= 7) {
            int rem = version;
            for (int i = 0; i < 12; i++) rem = rem << 1 ^ (rem >>> 11) * 0x1F25;
            int value = version << 12 | rem;
            for (int i = 0; i < 18; i++) {
                boolean bit = (value >>> i & 1) == 1;
                int row = i / 3, col = i % 3 + size - 11;
                dark[row][col] = bit; function[row][col] = true;
                dark[col][row] = bit; function[col][row] = true;
            }
        }
    }

    private void finder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) for (int dx = -4; dx <= 4; dx++) {
            int x = cx + dx, y = cy + dy;
            if (x < 0 || y < 0 || x >= size || y >= size) continue;
            int ring = Math.max(Math.abs(dx), Math.abs(dy));
            if (ring <= 3 && ring != 2) dark[y][x] = true;
            function[y][x] = true;
        }
    }

    private void alignment(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) dark[cy + dy][cx + dx] = Math.max(Math.abs(dx), Math.abs(dy)) != 1;
        reserveArea(cx - 2, cy - 2, 5);
    }

    /**
     * Reed-Solomon per block, then ISO interleaving of data and ECC codewords.
     */
    private void interleave(int version, int capacity) {
        int dataCodewords = capacity / 8, blocks = BLOCKS[version], shortLen = dataCodewords / blocks, shortBlocks = blocks - dataCodewords % blocks;
        byte[] all = new byte[dataCodewords];
        for (int i = 0; i < bits.size() / 8; i++) {
            int value = 0;
            for (int b = 0; b < 8; b++) value = value << 1 | bits.get(i * 8 + b);
            all[i] = (byte) value;
        }
        byte[][] data = new byte[blocks][], ecc = new byte[blocks][];
        int[] generator = generator(ECC[version]);
        int at = 0;
        for (int b = 0; b < blocks; b++) {
            int len = shortLen + (b < shortBlocks ? 0 : 1);
            data[b] = new byte[len];
            System.arraycopy(all, at, data[b], 0, len);
            at += len;
            ecc[b] = remainder(data[b], generator);
        }
        int maxLen = shortLen + (shortBlocks < blocks ? 1 : 0);
        List<Byte> stream = new ArrayList<>();
        for (int i = 0; i < maxLen; i++) for (int b = 0; b < blocks; b++) if (i < data[b].length) stream.add(data[b][i]);
        for (int i = 0; i < ECC[version]; i++) for (int b = 0; b < blocks; b++) stream.add(ecc[b][i]);
        int atBit = 0;
        boolean upward = true;
        for (int col = size - 1; col > 0; col -= 2) {
            if (col == 6) col--;
            for (int count = 0; count < size; count++) {
                int row = upward ? size - 1 - count : count;
                for (int x = col; x > col - 2; x--) {
                    if (function[row][x]) continue;
                    dark[row][x] = atBit < stream.size() * 8 && (stream.get(atBit / 8) >> 7 - atBit % 8 & 1) == 1;
                    atBit++;
                }
            }
            upward = !upward;
        }
    }

    /**
     * GF(256) generator polynomial for n EC codewords, product of (x - a^i),
     * as n coefficients highest degree first (the implicit leading 1 dropped).
     */
    private static int[] generator(int n) {
        int[] poly = { 1 };
        int root = 1;
        for (int i = 0; i < n; i++) {
            int[] next = new int[poly.length + 1];
            for (int k = 0; k < poly.length; k++) {
                next[k + 1] ^= poly[k];
                next[k] ^= multiply(poly[k], root);
            }
            poly = next;
            root = multiply(root, 2);
        }
        int[] out = new int[n];
        for (int j = 0; j < n; j++) out[j] = poly[n - 1 - j];
        return out;
    }

    private static int multiply(int a, int b) {
        int result = 0;
        for (int i = 7; i >= 0; i--) {
            result = result << 1 ^ (result >>> 7) * 285;
            if ((b >>> i & 1) == 1) result ^= a;
        }
        return result;
    }

    private static byte[] remainder(byte[] data, int[] generator) {
        int[] root = new int[generator.length];
        for (byte value : data) {
            int factor = (value & 0xFF) ^ root[0];
            System.arraycopy(root, 1, root, 0, root.length - 1);
            root[root.length - 1] = 0;
            for (int j = 0; j < root.length; j++) root[j] ^= multiply(generator[j], factor);
        }
        byte[] out = new byte[root.length];
        for (int i = 0; i < root.length; i++) out[i] = (byte) root[i];
        return out;
    }

    private boolean[][] masked(int mask) {
        boolean[][] out = new boolean[size][size];
        for (int row = 0; row < size; row++) for (int col = 0; col < size; col++) out[row][col] = dark[row][col] ^ (function[row][col] ? false : maskHit(mask, row, col));
        return out;
    }

    private static boolean maskHit(int mask, int row, int col) {
        switch (mask) {
            case 0: return (row + col) % 2 == 0;
            case 1: return row % 2 == 0;
            case 2: return col % 3 == 0;
            case 3: return (row + col) % 3 == 0;
            case 4: return (row / 2 + col / 3) % 2 == 0;
            case 5: return row * col % 2 + row * col % 3 == 0;
            case 6: return (row * col % 2 + row * col % 3) % 2 == 0;
            default: return ((row + col) % 2 + row * col % 3) % 2 == 0;
        }
    }

    /**
     * 15-bit format: EC level M (00) + mask, BCH(15,5) with generator 0x537, XOR 0x5412.
     */
    private void paintFormat(boolean[][] grid, int mask) {
        int data = mask, rem = data;
        for (int i = 0; i < 10; i++) rem = rem << 1 ^ (rem >>> 9) * 0x537;
        int value = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) grid[i][8] = (value >>> i & 1) == 1;
        grid[7][8] = (value >>> 6 & 1) == 1;
        grid[8][8] = (value >>> 7 & 1) == 1;
        grid[8][7] = (value >>> 8 & 1) == 1;
        for (int i = 9; i < 15; i++) grid[8][14 - i] = (value >>> i & 1) == 1;
        for (int i = 0; i < 8; i++) grid[8][size - 1 - i] = (value >>> i & 1) == 1;
        for (int i = 8; i < 15; i++) grid[size - 15 + i][8] = (value >>> i & 1) == 1;
        grid[size - 8][8] = true;
    }

    /**
     * The four standard penalty rules, straight from the spec.
     */
    private int penalty(boolean[][] grid) {
        int score = 0, darkCount = 0;
        for (int i = 0; i < size; i++) {
            int runRow = 0, runCol = 0;
            for (int j = 0; j < size; j++) {
                if (grid[i][j]) darkCount++;
                runRow = grid[i][j] ? runRow + 1 : 0;
                runCol = grid[j][i] ? runCol + 1 : 0;
                if (runRow == 5) score += 3; else if (runRow > 5) score++;
                if (runCol == 5) score += 3; else if (runCol > 5) score++;
                if (j + 2 <= size && i + 2 <= size) {
                    boolean a = grid[i][j];
                    if (grid[i][j + 1] == a && grid[i + 1][j] == a && grid[i + 1][j + 1] == a) score += 3;
                }
            }
            score += patternPenalty(grid, i, true) + patternPenalty(grid, i, false);
        }
        int percent = darkCount * 100 / (size * size);
        score += Math.abs(percent - 50) / 5 * 10;
        return score;
    }

    /**
     * Rule 3: 1:1:3:1:1 with quiet space on one side, in rows and columns.
     */
    private int patternPenalty(boolean[][] grid, int line, boolean horizontal) {
        int score = 0;
        for (int start = 0; start + 11 <= size; start++) {
            if (matches(grid, line, start, horizontal) || matchesReverse(grid, line, start, horizontal)) score += 40;
        }
        return score;
    }

    private boolean matches(boolean[][] grid, int line, int start, boolean horizontal) {
        int[] pattern = { 1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0 };
        for (int k = 0; k < 11; k++) if (gridAt(grid, line, start + k, horizontal) != (pattern[k] == 1)) return false;
        return true;
    }

    private boolean matchesReverse(boolean[][] grid, int line, int start, boolean horizontal) {
        int[] pattern = { 0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1 };
        for (int k = 0; k < 11; k++) if (gridAt(grid, line, start + k, horizontal) != (pattern[k] == 1)) return false;
        return true;
    }

    private boolean gridAt(boolean[][] grid, int line, int at, boolean horizontal) {
        return horizontal ? grid[line][at] : grid[at][line];
    }

    private void set(int x, int y, boolean value) {
        dark[y][x] = value;
        function[y][x] = true;
    }

    private void reserve(int x, int y) {
        function[y][x] = true;
    }

    private void reserveArea(int x, int y, int span) {
        for (int dy = 0; dy < span; dy++) for (int dx = 0; dx < span; dx++) function[y + dy][x + dx] = true;
    }
}
