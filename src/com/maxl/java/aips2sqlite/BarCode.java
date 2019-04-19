/*
Copyright (c) 2014 Max Lungarella

This file is part of Aips2SQLite.

Aips2SQLite is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.maxl.java.aips2sqlite;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.krysalis.barcode4j.impl.upcean.EAN13Bean;
import org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider;
import org.krysalis.barcode4j.tools.UnitConv;

public class BarCode {


	public BarCode() {

	}

	/**
	 * Encode image to string
	 * @param image: The image to encode
	 * @param type: jpeg, bmp, png,...
	 * @return encoded string
	 */
    private String encodeImgAsString(BufferedImage image, String type)
    {
        String imageString = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            ImageIO.write(image, type, bos);
            byte[] imageBytes = bos.toByteArray();

            Base64.Encoder encoder = Base64.getEncoder();
            imageString = encoder.encodeToString(imageBytes);

            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imageString;
    }

	public String encode(String eanStr) throws IOException {
		String barcodeBase64 = "";

        try {
            // Create EAN-13 barcode bean
            EAN13Bean bean = new EAN13Bean();
            // Set dot-per-inch (150 is pretty low-res)
            final int dpi = 150;
            // Configure barcode generator
            bean.setModuleWidth(UnitConv.in2mm(2.0f/dpi)); //3.0f/dpi // Makes narrow bar width exactly one pixel -> sets width
            bean.setBarHeight(10.0);	// 6.0 // Bar code height in mm -> sets height
            bean.doQuietZone(true);		// Set to "true" to visualize first digit
            bean.setQuietZone(5.0);		// Set size of quite zone
            bean.setFontName("Helvetica");
            bean.setFontSize(2.0);		// Font size in mm

           	boolean antiAlias = false;
           	int orientation = 0;
           	BitmapCanvasProvider canvas = new BitmapCanvasProvider(
           			dpi, BufferedImage.TYPE_BYTE_BINARY, antiAlias, orientation);
            // Generate barcode
            bean.generateBarcode(canvas, eanStr);

            // Get png image encoded as base64 string
            barcodeBase64 = encodeImgAsString(canvas.getBufferedImage(), "png");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Return html string
        return generateHtmlString(barcodeBase64, eanStr);
	}

    public String generateHtmlString(String b, String ean) {
		return ("<img id=\"" + ean + "\" src=\"data:image/png;base64," + b + "\" style=\"margin:0px 0px 15px 0px;\" width=\"320\" "
				+ "onmouseup=\"addShoppingCart(this)\" />");
    }

	public String generateHtmlPage(String b) {
		String base64Str = "<!DOCTYPE html><html><body>" +
				"<img style=\"max-width:100%25; width:auto; height:auto;\" src=\"data:image/png;base64," + b + "\"/>"
				+ "</body></html>";

		saveHtmlToFile(base64Str, "picb64.html");

		return base64Str;
	}

	public void saveHtmlToFile(String htmlStr, String fileName) {
		try {
			PrintWriter pw = new PrintWriter(fileName);
			pw.print(htmlStr);
			pw.close();
		} catch(FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}
