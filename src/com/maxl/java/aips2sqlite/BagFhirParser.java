/*
Copyright (c) 2026 ywesee GmbH

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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class BagFhirParser {

	public static class FhirPack {
		public String gtin = "";
		public String description = "";
		public String category = "";
		public String publicPrice = "";
		public String exFactoryPrice = "";
		public String exFactoryPriceValidFrom = "";
		public String swissmedicNo8 = "";
	}

	public static class FhirPreparation {
		public String name = "";
		public String swissmedicNo5 = "";
		public String atcCode = "";
		public String orgGenCode = "";
		public int costShare = 0;
		public List<FhirPack> packs = new ArrayList<>();
	}

	private List<FhirPreparation> prepList = new ArrayList<>();
	private ObjectMapper mapper = new ObjectMapper();

	public void parse(String filename) {
		try {
			FileInputStream fis = new FileInputStream(filename);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
			String line;
			int num_lines = 0;
			while ((line = br.readLine()) != null) {
				if (line.trim().isEmpty())
					continue;
				JsonNode bundle = mapper.readTree(line);
				FhirPreparation prep = jsonToPreparation(bundle);
				prepList.add(prep);
				num_lines++;
				if (num_lines % 500 == 0)
					System.out.print("\rProcessing BAG FHIR NDJSON file... " + num_lines);
			}
			br.close();
			System.out.println("\rProcessing BAG FHIR NDJSON file... " + num_lines + " preparations");
		} catch (Exception e) {
			System.err.println(">> Error in BagFhirParser.parse: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private FhirPreparation jsonToPreparation(JsonNode bundle) {
		FhirPreparation preparation = new FhirPreparation();
		JsonNode entries = bundle.get("entry");
		if (entries == null || !entries.isArray())
			return preparation;

		String language = CmlOptions.DB_LANGUAGE.isEmpty() ? "de" : CmlOptions.DB_LANGUAGE;

		for (JsonNode entry : entries) {
			JsonNode resource = entry.get("resource");
			if (resource == null) continue;
			String resourceType = getTextValue(resource, "resourceType");

			if (resourceType.equals("MedicinalProductDefinition")) {
				parseMedicinalProduct(resource, preparation, language);
			} else if (resourceType.equals("RegulatedAuthorization")) {
				parseRegulatedAuthorization(resource, preparation);
			} else if (resourceType.equals("PackagedProductDefinition")) {
				parsePackagedProduct(resource, bundle, preparation);
			}
		}
		return preparation;
	}

	private void parseMedicinalProduct(JsonNode resource, FhirPreparation preparation, String language) {
		// ATC code from first classification
		JsonNode classifications = resource.get("classification");
		if (classifications != null && classifications.isArray()) {
			for (JsonNode classification : classifications) {
				JsonNode coding = getFirstCoding(classification);
				if (coding == null) continue;
				String code = getTextValue(coding, "code");

				// https://fhir.ch/ig/ch-epl/CodeSystem-ch-epl-foph-product-type.html
				if (code.equals("756001003001")) {
					preparation.orgGenCode = "G";
				} else if (code.equals("756001003002")) {
					preparation.orgGenCode = "O";
				} else if (!code.startsWith("756") && !code.isEmpty()) {
					// ATC code
					preparation.atcCode = code;
				}
			}
		}

		// Product name by language
		JsonNode names = resource.get("name");
		if (names != null && names.isArray()) {
			for (JsonNode name : names) {
				String productName = getTextValue(name, "productName");
				JsonNode usage = name.get("usage");
				if (usage != null && usage.isArray() && usage.size() > 0) {
					JsonNode langCoding = usage.get(0).path("language").path("coding");
					if (langCoding.isArray() && langCoding.size() > 0) {
						String langCode = getTextValue(langCoding.get(0), "code");
						if (langCode.startsWith(language)) {
							preparation.name = productName;
						}
					}
				}
			}
		}
	}

	private void parseRegulatedAuthorization(JsonNode resource, FhirPreparation preparation) {
		// Only process the RegulatedAuthorization that has an identifier (Swissmedic number)
		// and does NOT have a subject (those with subject are pack-level authorizations)
		JsonNode identifiers = resource.get("identifier");
		if (identifiers != null && identifiers.isArray() && identifiers.size() > 0) {
			JsonNode subject = resource.get("subject");
			if (subject == null || !subject.isArray() || subject.size() == 0) {
				String value = getTextValue(identifiers.get(0), "value");
				if (value.length() == 5) {
					preparation.swissmedicNo5 = value;
				}
			}
		}
	}

	private void parsePackagedProduct(JsonNode resource, JsonNode bundle, FhirPreparation preparation) {
		FhirPack pack = new FhirPack();

		// GTIN
		JsonNode packaging = resource.get("packaging");
		if (packaging != null) {
			JsonNode identifiers = packaging.get("identifier");
			if (identifiers != null && identifiers.isArray() && identifiers.size() > 0) {
				pack.gtin = getTextValue(identifiers.get(0), "value");
			}
		}

		// Derive SwissmedicNo8 from GTIN (digits 4-12 of 13-digit EAN)
		if (pack.gtin.length() == 13) {
			pack.swissmedicNo8 = pack.gtin.substring(4, 12);
		}

		// Description
		pack.description = getTextValue(resource, "description");

		// Legal status (category)
		JsonNode legalStatus = resource.get("legalStatusOfSupply");
		if (legalStatus != null && legalStatus.isArray() && legalStatus.size() > 0) {
			JsonNode coding = getFirstCoding(legalStatus.get(0).path("code"));
			if (coding != null) {
				String code = getTextValue(coding, "code");
				pack.category = mapLegalStatusToCategory(code);
			}
		}

		// Find prices from RegulatedAuthorization that references this PackagedProductDefinition
		String resourceId = getTextValue(resource, "id");
		if (!resourceId.isEmpty()) {
			fillPricesFromAuthorization(bundle, resourceId, pack, preparation);
		}

		if (!pack.gtin.isEmpty()) {
			preparation.packs.add(pack);
		}
	}

	private void fillPricesFromAuthorization(JsonNode bundle, String packageResourceId,
			FhirPack pack, FhirPreparation preparation) {
		JsonNode entries = bundle.get("entry");
		if (entries == null) return;

		for (JsonNode entry : entries) {
			JsonNode resource = entry.get("resource");
			if (resource == null) continue;
			if (!"RegulatedAuthorization".equals(getTextValue(resource, "resourceType")))
				continue;

			// Check if this authorization references our package
			JsonNode subjects = resource.get("subject");
			if (subjects == null || !subjects.isArray() || subjects.size() == 0)
				continue;
			String reference = getTextValue(subjects.get(0), "reference");
			if (!reference.equals("CHIDMPPackagedProductDefinition/" + packageResourceId))
				continue;

			// Process reimbursementSL extensions
			JsonNode extensions = resource.get("extension");
			if (extensions == null) continue;

			for (JsonNode ext : extensions) {
				String extUrl = getTextValue(ext, "url");
				if (!extUrl.equals("http://fhir.ch/ig/ch-epl/StructureDefinition/reimbursementSL"))
					continue;

				JsonNode subExtensions = ext.get("extension");
				if (subExtensions == null) continue;

				for (JsonNode subExt : subExtensions) {
					String subUrl = getTextValue(subExt, "url");
					if (subUrl.equals("http://fhir.ch/ig/ch-epl/StructureDefinition/productPrice")) {
						parseProductPrice(subExt, pack);
					} else if (subUrl.equals("costShare")) {
						JsonNode valueInteger = subExt.get("valueInteger");
						if (valueInteger != null) {
							preparation.costShare = valueInteger.asInt();
						}
					}
				}
			}
		}
	}

	private void parseProductPrice(JsonNode productPriceExt, FhirPack pack) {
		JsonNode priceExtensions = productPriceExt.get("extension");
		if (priceExtensions == null) return;

		String priceType = "";
		String priceValue = "";
		String changeDate = "";

		for (JsonNode ext : priceExtensions) {
			String url = getTextValue(ext, "url");
			if (url.equals("type")) {
				JsonNode coding = ext.path("valueCodeableConcept").path("coding");
				if (coding.isArray() && coding.size() > 0) {
					String code = getTextValue(coding.get(0), "code");
					if (code.equals("756002005001")) {
						priceType = "public";
					} else if (code.equals("756002005002")) {
						priceType = "exfactory";
					}
				}
			} else if (url.equals("value")) {
				JsonNode money = ext.get("valueMoney");
				if (money != null) {
					JsonNode value = money.get("value");
					if (value != null) {
						pack.category = pack.category; // no-op, keep existing
						priceValue = String.format("%.2f", value.asDouble());
					}
				}
			} else if (url.equals("changeDate")) {
				JsonNode dateNode = ext.get("valueDate");
				if (dateNode != null) {
					changeDate = dateNode.asText();
				}
			}
		}

		if (priceType.equals("public")) {
			pack.publicPrice = priceValue;
		} else if (priceType.equals("exfactory")) {
			pack.exFactoryPrice = priceValue;
			pack.exFactoryPriceValidFrom = changeDate;
		}
	}

	private String mapLegalStatusToCategory(String code) {
		switch (code) {
			case "756005022001": return "A";
			case "756005022003": return "B";
			case "756005022005": return "C";
			case "756005022007": return "D";
			case "756005022009": return "E";
			default: return "";
		}
	}

	// --- Data access methods ---

	public List<FhirPreparation> getPrepList() {
		return prepList;
	}

	/**
	 * Build flags map: SwissmedicNo5 -> OrgGenCode;FlagSB20;SwissmedicCategory;
	 * Compatible with DispoParse.getSLMap() output format.
	 */
	public HashMap<String, String> buildFlagsMap() {
		HashMap<String, String> flagsMap = new HashMap<>();
		for (FhirPreparation prep : prepList) {
			String orgGen = prep.orgGenCode;
			// Map costShare to FlagSB20: 20% or more -> "Y", else -> "N"
			String flagSB20 = (prep.costShare >= 20) ? "Y" : "N";
			// Get category from first pack (same for all packs of a preparation)
			String category = "";
			if (!prep.packs.isEmpty()) {
				category = prep.packs.get(0).category;
			}
			flagsMap.put(prep.swissmedicNo5, orgGen + ";" + flagSB20 + ";" + category + ";");
		}
		return flagsMap;
	}

	/**
	 * Build exfactory price map: GTIN -> price
	 * Compatible with DispoParse.getSLMap() output format.
	 */
	public HashMap<String, String> buildExfactoryPriceMap() {
		HashMap<String, String> priceMap = new HashMap<>();
		for (FhirPreparation prep : prepList) {
			for (FhirPack pack : prep.packs) {
				if (!pack.gtin.isEmpty() && pack.gtin.length() == 13 && !pack.exFactoryPrice.isEmpty()) {
					priceMap.put(pack.gtin, pack.exFactoryPrice);
				}
			}
		}
		return priceMap;
	}

	/**
	 * Build public price map: GTIN -> price
	 * Compatible with DispoParse.getSLMap() output format.
	 */
	public HashMap<String, String> buildPublicPriceMap() {
		HashMap<String, String> priceMap = new HashMap<>();
		for (FhirPreparation prep : prepList) {
			for (FhirPack pack : prep.packs) {
				if (!pack.gtin.isEmpty() && pack.gtin.length() == 13 && !pack.publicPrice.isEmpty()) {
					priceMap.put(pack.gtin, pack.publicPrice);
				}
			}
		}
		return priceMap;
	}

	// --- Helper methods ---

	private String getTextValue(JsonNode node, String field) {
		JsonNode child = node.get(field);
		if (child != null && child.isTextual())
			return child.asText();
		return "";
	}

	private JsonNode getFirstCoding(JsonNode node) {
		JsonNode coding = node.path("coding");
		if (coding.isArray() && coding.size() > 0)
			return coding.get(0);
		return null;
	}
}
