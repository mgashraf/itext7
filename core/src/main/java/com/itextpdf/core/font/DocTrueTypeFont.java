package com.itextpdf.core.font;

import com.itextpdf.basics.font.FontEncoding;
import com.itextpdf.basics.font.TrueTypeFont;
import com.itextpdf.basics.font.otf.Glyph;
import com.itextpdf.core.pdf.PdfArray;
import com.itextpdf.core.pdf.PdfDictionary;
import com.itextpdf.core.pdf.PdfName;
import com.itextpdf.core.pdf.PdfNumber;
import com.itextpdf.core.pdf.PdfStream;
import com.itextpdf.core.pdf.PdfString;

class DocTrueTypeFont extends TrueTypeFont {

    private PdfStream fontFile;
    private PdfName fontFileName;
    private PdfName subtype;

    private DocTrueTypeFont(String fontName) {
        super();
        getFontNames().setFontName(fontName);
    }

    public static TrueTypeFont createSimpleFontProgram(PdfDictionary fontDictionary, FontEncoding fontEncoding) {
        PdfName baseFontName = fontDictionary.getAsName(PdfName.BaseFont);
        String baseFont;
        if (baseFontName != null) {
            baseFont = baseFontName.getValue();
        } else {
            baseFont = DocFontUtils.createRandomFontName();
        }
        DocTrueTypeFont fontProgram = new DocTrueTypeFont(baseFont);
        PdfNumber firstCharNumber = fontDictionary.getAsNumber(PdfName.FirstChar);
        int firstChar = firstCharNumber != null ? Math.min(firstCharNumber.getIntValue(), 0) : 0;
        int[] widths = DocFontUtils.convertSimpleWidthsArray(fontDictionary.getAsArray(PdfName.Widths), firstChar);

        for (int i = 0; i < 256; i++) {
            int width = i - firstChar < widths.length ? widths[i - firstChar] : 0;
            Glyph glyph = new Glyph(i, width, fontEncoding.getUnicode(i));
            fontProgram.codeToGlyph.put(i, glyph);
            if (glyph.getUnicode() != null) {
                fontProgram.unicodeToGlyph.put(glyph.getUnicode(), glyph);
            }
        }
        fillFontDescriptor(fontProgram, fontDictionary.getAsDictionary(PdfName.FontDescriptor));

        return fontProgram;
    }

    public PdfStream getFontFile() {
        return fontFile;
    }

    public PdfName getFontFileName() {
        return fontFileName;
    }

    public PdfName getSubtype() {
        return subtype;
    }

    static void fillFontDescriptor(DocTrueTypeFont font, PdfDictionary fontDesc) {
        if (fontDesc == null) {
            return;
        }
        PdfNumber v = fontDesc.getAsNumber(PdfName.Ascent);
        if (v != null) {
            font.setTypoAscender(v.getIntValue());
        }
        v = fontDesc.getAsNumber(PdfName.Descent);
        if (v != null) {
            font.setTypoDescender(v.getIntValue());
        }
        v = fontDesc.getAsNumber(PdfName.CapHeight);
        if (v != null) {
            font.setCapHeight(v.getIntValue());
        }
        v = fontDesc.getAsNumber(PdfName.XHeight);
        if (v != null) {
            font.setXHeight(v.getIntValue());
        }
        v = fontDesc.getAsNumber(PdfName.ItalicAngle);
        if (v != null) {
            font.setItalicAngle(v.getIntValue());
        }
        v = fontDesc.getAsNumber(PdfName.StemV);
        if (v != null) {
            font.setStemV(v.getIntValue());
        }
        v = fontDesc.getAsNumber(PdfName.StemH);
        if (v != null) {
            font.setStemH(v.getIntValue());
        }
        v = fontDesc.getAsNumber(PdfName.FontWeight);
        if (v != null) {
            font.setFontWeight(v.getIntValue());
        }

        PdfName fontStretch = fontDesc.getAsName(PdfName.FontStretch);
        if (fontStretch != null) {
            font.setFontWidth(fontStretch.getValue());
        }


        PdfArray bboxValue = fontDesc.getAsArray(PdfName.FontBBox);

        if (bboxValue != null) {
            int[] bbox = new int[4];
            //llx
            bbox[0] = bboxValue.getAsNumber(0).getIntValue();
            //lly
            bbox[1] = bboxValue.getAsNumber(1).getIntValue();
            //urx
            bbox[2] = bboxValue.getAsNumber(2).getIntValue();
            //ury
            bbox[3] = bboxValue.getAsNumber(3).getIntValue();

            if (bbox[0] > bbox[2]) {
                int t = bbox[0];
                bbox[0] = bbox[2];
                bbox[2] = t;
            }
            if (bbox[1] > bbox[3]) {
                int t = bbox[1];
                bbox[1] = bbox[3];
                bbox[3] = t;
            }
            font.setBbox(bbox);
        }

        PdfString fontFamily = fontDesc.getAsString(PdfName.FontFamily);
        if (fontFamily != null) {
            font.setFontFamily(fontFamily.getValue());
        }

        PdfNumber flagsValue = fontDesc.getAsNumber(PdfName.Flags);
        if (flagsValue != null) {
            int flags = flagsValue.getIntValue();
            if ((flags & 1) != 0) {
                font.setFixedPitch(true);
            }
            if ((flags & 262144) != 0) {
                font.setBold(true);
            }
        }

        PdfName[] fontFileNames = new PdfName[] {PdfName.FontFile, PdfName.FontFile2, PdfName.FontFile3};
        for (PdfName fontFile: fontFileNames) {
            if (fontDesc.containsKey(fontFile)) {
                font.fontFileName = fontFile;
                font.fontFile = fontDesc.getAsStream(fontFile);
                break;
            }
        }
        font.subtype = fontDesc.getAsName(PdfName.Subtype);
        if (font.subtype == null) {
            font.subtype = PdfName.TrueType;
        }
    }

}