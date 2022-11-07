package com.maxl.java.aips2sqlite;

//
// Diese Datei wurde mit der JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 generiert
// Siehe <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Änderungen an dieser Datei gehen bei einer Neukompilierung des Quellschemas verloren.
// Generiert: 2015.06.24 um 09:25:25 PM CEST
//

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;
import javax.xml.datatype.XMLGregorianCalendar;


/**
* <p>Java-Klasse für anonymous complex type.
*
* <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
*
* <pre>
* &lt;complexType>
*   &lt;complexContent>
*     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
*       &lt;sequence>
*         &lt;element name="ITEM" maxOccurs="unbounded" minOccurs="0">
*           &lt;complexType>
*             &lt;complexContent>
*               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
*                 &lt;sequence>
*                   &lt;element name="ATYPE">
*                     &lt;simpleType>
*                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
*                         &lt;maxLength value="1"/>
*                         &lt;enumeration value="PHARMA"/>
*                         &lt;enumeration value="NONPHARMA"/>
*                       &lt;/restriction>
*                     &lt;/simpleType>
*                   &lt;/element>
*                   &lt;element name="GTIN">
*                     &lt;simpleType>
*                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
*                         &lt;length value="14"/>
*                       &lt;/restriction>
*                     &lt;/simpleType>
*                   &lt;/element>
*                   &lt;element name="PHAR">
*                     &lt;simpleType>
*                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
*                         &lt;length value="7"/>
*                       &lt;/restriction>
*                     &lt;/simpleType>
*                   &lt;/element>
*                   &lt;element name="SWMC_AUTHNR">
*                     &lt;simpleType>
*                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
*                         &lt;length value="8"/>
*                       &lt;/restriction>
*                     &lt;/simpleType>
*                   &lt;/element>
*                   &lt;element name="NAME_DE">
*                     &lt;simpleType>
*                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
*                         &lt;maxLength value="80"/>
*                       &lt;/restriction>
*                     &lt;/simpleType>
*                   &lt;/element>
*                   &lt;element name="NAME_FR">
*                     &lt;simpleType>
*                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
*                         &lt;maxLength value="80"/>
*                       &lt;/restriction>
*                     &lt;/simpleType>
*                   &lt;/element>
*                   &lt;element name="ATC">
*                     &lt;simpleType>
*                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
*                         &lt;maxLength value="7"/>
*                       &lt;/restriction>
*                     &lt;/simpleType>
*                   &lt;/element>
*                   &lt;element name="AUTH_HOLDER_NAME">
*                     &lt;simpleType>
*                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
*                         &lt;maxLength value="101"/>
*                       &lt;/restriction>
*                     &lt;/simpleType>
*                   &lt;/element>
*                   &lt;element name="AUTH_HOLDER_GLN">
*                     &lt;simpleType>
*                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
*                         &lt;maxLength value="14"/>
*                       &lt;/restriction>
*                     &lt;/simpleType>
*                   &lt;/element>
*                 &lt;/sequence>
*                 &lt;attribute name="DT" use="required" type="{http://www.w3.org/2001/XMLSchema}dateTime" />
*               &lt;/restriction>
*             &lt;/complexContent>
*           &lt;/complexType>
*         &lt;/element>
*         &lt;element name="RESULT">
*           &lt;complexType>
*             &lt;complexContent>
*               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
*                 &lt;sequence>
*                   &lt;element name="OK_ERROR">
*                     &lt;simpleType>
*                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
*                         &lt;maxLength value="5"/>
*                         &lt;enumeration value="OK"/>
*                         &lt;enumeration value="ERROR"/>
*                       &lt;/restriction>
*                     &lt;/simpleType>
*                   &lt;/element>
*                   &lt;element name="NBR_RECORD" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
*                   &lt;element name="ERROR_CODE" minOccurs="0">
*                     &lt;simpleType>
*                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
*                         &lt;maxLength value="20"/>
*                       &lt;/restriction>
*                     &lt;/simpleType>
*                   &lt;/element>
*                   &lt;element name="MESSAGE" minOccurs="0">
*                     &lt;simpleType>
*                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
*                         &lt;maxLength value="2000"/>
*                       &lt;/restriction>
*                     &lt;/simpleType>
*                   &lt;/element>
*                 &lt;/sequence>
*               &lt;/restriction>
*             &lt;/complexContent>
*           &lt;/complexType>
*         &lt;/element>
*       &lt;/sequence>
*       &lt;attribute name="CREATION_DATETIME" use="required" type="{http://www.w3.org/2001/XMLSchema}dateTime" />
*     &lt;/restriction>
*   &lt;/complexContent>
* &lt;/complexType>
* </pre>
*
*
*/
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
 "item",
 "result"
})
@XmlRootElement(name = "ARTICLE")
public class Refdata {

 @XmlElement(name = "ITEM")
 protected List<Refdata.ITEM> item;
 @XmlElement(name = "RESULT", required = true)
 protected Refdata.RESULT result;
 @XmlAttribute(name = "CREATION_DATETIME", required = true)
 @XmlSchemaType(name = "dateTime")
 protected XMLGregorianCalendar creationdatetime;

 /**
  * Gets the value of the item property.
  *
  * <p>
  * This accessor method returns a reference to the live list,
  * not a snapshot. Therefore any modification you make to the
  * returned list will be present inside the JAXB object.
  * This is why there is not a <CODE>set</CODE> method for the item property.
  *
  * <p>
  * For example, to add a new item, do as follows:
  * <pre>
  *    getITEM().add(newItem);
  * </pre>
  *
  *
  * <p>
  * Objects of the following type(s) are allowed in the list
  * {@link Refdata.ITEM }
  *
  *
  */
 public List<Refdata.ITEM> getItem() {
     if (item == null) {
         item = new ArrayList<Refdata.ITEM>();
     }
     return this.item;
 }

 /**
  * Ruft den Wert der result-Eigenschaft ab.
  *
  * @return
  *     possible object is
  *     {@link Refdata.RESULT }
  *
  */
 public Refdata.RESULT getRESULT() {
     return result;
 }

 /**
  * Legt den Wert der result-Eigenschaft fest.
  *
  * @param value
  *     allowed object is
  *     {@link Refdata.RESULT }
  *
  */
 public void setRESULT(Refdata.RESULT value) {
     this.result = value;
 }

 /**
  * Ruft den Wert der creationdatetime-Eigenschaft ab.
  *
  * @return
  *     possible object is
  *     {@link XMLGregorianCalendar }
  *
  */
 public XMLGregorianCalendar getCREATIONDATETIME() {
     return creationdatetime;
 }

 /**
  * Legt den Wert der creationdatetime-Eigenschaft fest.
  *
  * @param value
  *     allowed object is
  *     {@link XMLGregorianCalendar }
  *
  */
 public void setCREATIONDATETIME(XMLGregorianCalendar value) {
     this.creationdatetime = value;
 }


 /**
  * <p>Java-Klasse für anonymous complex type.
  *
  * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
  *
  * <pre>
  * &lt;complexType>
  *   &lt;complexContent>
  *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
  *       &lt;sequence>
  *         &lt;element name="ATYPE">
  *           &lt;simpleType>
  *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
  *               &lt;maxLength value="1"/>
  *               &lt;enumeration value="PHARMA"/>
  *               &lt;enumeration value="NONPHARMA"/>
  *             &lt;/restriction>
  *           &lt;/simpleType>
  *         &lt;/element>
  *         &lt;element name="GTIN">
  *           &lt;simpleType>
  *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
  *               &lt;length value="14"/>
  *             &lt;/restriction>
  *           &lt;/simpleType>
  *         &lt;/element>
  *         &lt;element name="PHAR">
  *           &lt;simpleType>
  *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
  *               &lt;length value="7"/>
  *             &lt;/restriction>
  *           &lt;/simpleType>
  *         &lt;/element>
  *         &lt;element name="SWMC_AUTHNR">
  *           &lt;simpleType>
  *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
  *               &lt;length value="8"/>
  *             &lt;/restriction>
  *           &lt;/simpleType>
  *         &lt;/element>
  *         &lt;element name="NAME_DE">
  *           &lt;simpleType>
  *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
  *               &lt;maxLength value="80"/>
  *             &lt;/restriction>
  *           &lt;/simpleType>
  *         &lt;/element>
  *         &lt;element name="NAME_FR">
  *           &lt;simpleType>
  *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
  *               &lt;maxLength value="80"/>
  *             &lt;/restriction>
  *           &lt;/simpleType>
  *         &lt;/element>
  *         &lt;element name="ATC">
  *           &lt;simpleType>
  *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
  *               &lt;maxLength value="7"/>
  *             &lt;/restriction>
  *           &lt;/simpleType>
  *         &lt;/element>
  *         &lt;element name="AUTH_HOLDER_NAME">
  *           &lt;simpleType>
  *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
  *               &lt;maxLength value="101"/>
  *             &lt;/restriction>
  *           &lt;/simpleType>
  *         &lt;/element>
  *         &lt;element name="AUTH_HOLDER_GLN">
  *           &lt;simpleType>
  *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
  *               &lt;maxLength value="14"/>
  *             &lt;/restriction>
  *           &lt;/simpleType>
  *         &lt;/element>
  *       &lt;/sequence>
  *       &lt;attribute name="DT" use="required" type="{http://www.w3.org/2001/XMLSchema}dateTime" />
  *     &lt;/restriction>
  *   &lt;/complexContent>
  * &lt;/complexType>
  * </pre>
  *
  *
  */
 @XmlAccessorType(XmlAccessType.FIELD)
 @XmlType(name = "", propOrder = {
     "atype",
     "gtin",
     "phar",
     "swmcauthnr",
     "namede",
     "namefr",
     "atc",
     "authholdername",
     "authholdergln"
 })
 public static class ITEM {

     @XmlElement(name = "ATYPE", required = true)
     protected String atype;
     @XmlElement(name = "GTIN", required = true)
     protected String gtin;
     @XmlElement(name = "PHAR", required = true)
     protected String phar;
     @XmlElement(name = "SWMC_AUTHNR", required = true)
     protected String swmcauthnr;
     @XmlElement(name = "NAME_DE", required = true)
     protected String namede;
     @XmlElement(name = "NAME_FR", required = true)
     protected String namefr;
     @XmlElement(name = "ATC", required = true)
     protected String atc;
     @XmlElement(name = "AUTH_HOLDER_NAME", required = true)
     protected String authholdername;
     @XmlElement(name = "AUTH_HOLDER_GLN", required = true)
     protected String authholdergln;
     @XmlAttribute(name = "DT", required = true)
     @XmlSchemaType(name = "dateTime")
     protected XMLGregorianCalendar dt;

     /**
      * Ruft den Wert der atype-Eigenschaft ab.
      *
      * @return
      *     possible object is
      *     {@link String }
      *
      */
     public String getATYPE() {
         return atype;
     }

     /**
      * Legt den Wert der atype-Eigenschaft fest.
      *
      * @param value
      *     allowed object is
      *     {@link String }
      *
      */
     public void setATYPE(String value) {
         this.atype = value;
     }

     /**
      * Ruft den Wert der gtin-Eigenschaft ab.
      *
      * @return
      *     possible object is
      *     {@link String }
      *
      */
     public String getGtin() {
         return gtin;
     }

     /**
      * Legt den Wert der gtin-Eigenschaft fest.
      *
      * @param value
      *     allowed object is
      *     {@link String }
      *
      */
     public void setGTIN(String value) {
         this.gtin = value;
     }

     /**
      * Ruft den Wert der phar-Eigenschaft ab.
      *
      * @return
      *     possible object is
      *     {@link String }
      *
      */
     public String getPhar() {
         return phar;
     }

     /**
      * Legt den Wert der phar-Eigenschaft fest.
      *
      * @param value
      *     allowed object is
      *     {@link String }
      *
      */
     public void setPhar(String value) {
         this.phar = value;
     }

     /**
      * Ruft den Wert der swmcauthnr-Eigenschaft ab.
      *
      * @return
      *     possible object is
      *     {@link String }
      *
      */
     public String getSWMCAUTHNR() {
         return swmcauthnr;
     }

     /**
      * Legt den Wert der swmcauthnr-Eigenschaft fest.
      *
      * @param value
      *     allowed object is
      *     {@link String }
      *
      */
     public void setSWMCAUTHNR(String value) {
         this.swmcauthnr = value;
     }

     /**
      * Ruft den Wert der namede-Eigenschaft ab.
      *
      * @return
      *     possible object is
      *     {@link String }
      *
      */
     public String getNameDE() {
         return namede;
     }

     /**
      * Legt den Wert der namede-Eigenschaft fest.
      *
      * @param value
      *     allowed object is
      *     {@link String }
      *
      */
     public void setNameDE(String value) {
         this.namede = value;
     }

     /**
      * Ruft den Wert der namefr-Eigenschaft ab.
      *
      * @return
      *     possible object is
      *     {@link String }
      *
      */
     public String getNameFR() {
         return namefr;
     }

     /**
      * Legt den Wert der namefr-Eigenschaft fest.
      *
      * @param value
      *     allowed object is
      *     {@link String }
      *
      */
     public void setNameFR(String value) {
         this.namefr = value;
     }

     /**
      * Ruft den Wert der atc-Eigenschaft ab.
      *
      * @return
      *     possible object is
      *     {@link String }
      *
      */
     public String getATC() {
         return atc;
     }

     /**
      * Legt den Wert der atc-Eigenschaft fest.
      *
      * @param value
      *     allowed object is
      *     {@link String }
      *
      */
     public void setATC(String value) {
         this.atc = value;
     }

     /**
      * Ruft den Wert der authholdername-Eigenschaft ab.
      *
      * @return
      *     possible object is
      *     {@link String }
      *
      */
     public String getAUTHHOLDERNAME() {
         return authholdername;
     }

     /**
      * Legt den Wert der authholdername-Eigenschaft fest.
      *
      * @param value
      *     allowed object is
      *     {@link String }
      *
      */
     public void setAUTHHOLDERNAME(String value) {
         this.authholdername = value;
     }

     /**
      * Ruft den Wert der authholdergln-Eigenschaft ab.
      *
      * @return
      *     possible object is
      *     {@link String }
      *
      */
     public String getAUTHHOLDERGLN() {
         return authholdergln;
     }

     /**
      * Legt den Wert der authholdergln-Eigenschaft fest.
      *
      * @param value
      *     allowed object is
      *     {@link String }
      *
      */
     public void setAUTHHOLDERGLN(String value) {
         this.authholdergln = value;
     }

     /**
      * Ruft den Wert der dt-Eigenschaft ab.
      *
      * @return
      *     possible object is
      *     {@link XMLGregorianCalendar }
      *
      */
     public XMLGregorianCalendar getDT() {
         return dt;
     }

     /**
      * Legt den Wert der dt-Eigenschaft fest.
      *
      * @param value
      *     allowed object is
      *     {@link XMLGregorianCalendar }
      *
      */
     public void setDT(XMLGregorianCalendar value) {
         this.dt = value;
     }

 }


 /**
  * <p>Java-Klasse für anonymous complex type.
  *
  * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
  *
  * <pre>
  * &lt;complexType>
  *   &lt;complexContent>
  *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
  *       &lt;sequence>
  *         &lt;element name="OK_ERROR">
  *           &lt;simpleType>
  *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
  *               &lt;maxLength value="5"/>
  *               &lt;enumeration value="OK"/>
  *               &lt;enumeration value="ERROR"/>
  *             &lt;/restriction>
  *           &lt;/simpleType>
  *         &lt;/element>
  *         &lt;element name="NBR_RECORD" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
  *         &lt;element name="ERROR_CODE" minOccurs="0">
  *           &lt;simpleType>
  *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
  *               &lt;maxLength value="20"/>
  *             &lt;/restriction>
  *           &lt;/simpleType>
  *         &lt;/element>
  *         &lt;element name="MESSAGE" minOccurs="0">
  *           &lt;simpleType>
  *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
  *               &lt;maxLength value="2000"/>
  *             &lt;/restriction>
  *           &lt;/simpleType>
  *         &lt;/element>
  *       &lt;/sequence>
  *     &lt;/restriction>
  *   &lt;/complexContent>
  * &lt;/complexType>
  * </pre>
  *
  *
  */
 @XmlAccessorType(XmlAccessType.FIELD)
 @XmlType(name = "", propOrder = {
     "okerror",
     "nbrrecord",
     "errorcode",
     "message"
 })
 public static class RESULT {

     @XmlElement(name = "OK_ERROR", required = true)
     protected String okerror;
     @XmlElement(name = "NBR_RECORD")
     protected Integer nbrrecord;
     @XmlElement(name = "ERROR_CODE")
     protected String errorcode;
     @XmlElement(name = "MESSAGE")
     protected String message;

     /**
      * Ruft den Wert der okerror-Eigenschaft ab.
      *
      * @return
      *     possible object is
      *     {@link String }
      *
      */
     public String getOKERROR() {
         return okerror;
     }

     /**
      * Legt den Wert der okerror-Eigenschaft fest.
      *
      * @param value
      *     allowed object is
      *     {@link String }
      *
      */
     public void setOKERROR(String value) {
         this.okerror = value;
     }

     /**
      * Ruft den Wert der nbrrecord-Eigenschaft ab.
      *
      * @return
      *     possible object is
      *     {@link Integer }
      *
      */
     public Integer getNBRRECORD() {
         return nbrrecord;
     }

     /**
      * Legt den Wert der nbrrecord-Eigenschaft fest.
      *
      * @param value
      *     allowed object is
      *     {@link Integer }
      *
      */
     public void setNBRRECORD(Integer value) {
         this.nbrrecord = value;
     }

     /**
      * Ruft den Wert der errorcode-Eigenschaft ab.
      *
      * @return
      *     possible object is
      *     {@link String }
      *
      */
     public String getERRORCODE() {
         return errorcode;
     }

     /**
      * Legt den Wert der errorcode-Eigenschaft fest.
      *
      * @param value
      *     allowed object is
      *     {@link String }
      *
      */
     public void setERRORCODE(String value) {
         this.errorcode = value;
     }

     /**
      * Ruft den Wert der message-Eigenschaft ab.
      *
      * @return
      *     possible object is
      *     {@link String }
      *
      */
     public String getMESSAGE() {
         return message;
     }

     /**
      * Legt den Wert der message-Eigenschaft fest.
      *
      * @param value
      *     allowed object is
      *     {@link String }
      *
      */
     public void setMESSAGE(String value) {
         this.message = value;
     }

 }

}
