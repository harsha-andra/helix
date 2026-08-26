package com.harshaandra.helix.api.soap;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Bridges java.time to the XMLGregorianCalendar that JAXB produces for xs:date and xs:dateTime.
 *
 * The alternative is an XJC binding file that maps the XSD types straight to LocalDate/Instant.
 * That is tidier in the endpoint but puts a build-time binding customisation between the schema
 * and anyone reading it, and partner integrators reading claims.xsd would then see types that
 * do not match what our own code uses. Converting explicitly in one small class is the more
 * honest trade.
 */
final class XmlTypes {

    private static final DatatypeFactory FACTORY;

    static {
        try {
            FACTORY = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException("No JAXP DatatypeFactory available", e);
        }
    }

    private XmlTypes() {
    }

    static XMLGregorianCalendar toXmlDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return FACTORY.newXMLGregorianCalendarDate(
                date.getYear(), date.getMonthValue(), date.getDayOfMonth(),
                javax.xml.datatype.DatatypeConstants.FIELD_UNDEFINED);
    }

    static XMLGregorianCalendar toXmlDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        ZonedDateTime utc = instant.atZone(ZoneOffset.UTC);
        return FACTORY.newXMLGregorianCalendar(java.util.GregorianCalendar.from(utc));
    }
}
