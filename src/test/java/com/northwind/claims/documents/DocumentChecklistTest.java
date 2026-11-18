package com.northwind.claims.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.northwind.claims.model.ClaimType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DocumentChecklistTest {
    private static final long THRESHOLD = 1_000_000;

    @Test
    void autoBelowThresholdNeedsPoliceOrAccidentReport() {
        assertEquals(List.of("claim form", "photo of damage", "police report or accident report"),
                DocumentChecklist.required(ClaimType.AUTO, 320_000));
    }

    @Test
    void theftBelowThresholdNeedsPoliceReport() {
        assertEquals(List.of("claim form", "photo of damage", "police report"),
                DocumentChecklist.required(ClaimType.THEFT, 500_000));
    }

    @Test
    void waterDamageBelowThresholdNeedsPlumberOrContractorReport() {
        assertEquals(List.of("claim form", "photo of damage", "plumber or contractor report"),
                DocumentChecklist.required(ClaimType.WATER_DAMAGE, 500_000));
    }

    @Test
    void propertyBelowThresholdNeedsOnlyBaseDocuments() {
        assertEquals(List.of("claim form", "photo of damage"),
                DocumentChecklist.required(ClaimType.PROPERTY, 500_000));
    }

    @Test
    void autoAtThresholdAlsoNeedsTwoRepairEstimates() {
        assertEquals(List.of("claim form", "photo of damage", "police report or accident report", "two repair estimates"),
                DocumentChecklist.required(ClaimType.AUTO, THRESHOLD));
    }

    @Test
    void theftAboveThresholdAlsoNeedsTwoRepairEstimates() {
        assertEquals(List.of("claim form", "photo of damage", "police report", "two repair estimates"),
                DocumentChecklist.required(ClaimType.THEFT, 2_100_000));
    }

    @Test
    void waterDamageAboveThresholdAlsoNeedsTwoRepairEstimates() {
        assertEquals(List.of("claim form", "photo of damage", "plumber or contractor report", "two repair estimates"),
                DocumentChecklist.required(ClaimType.WATER_DAMAGE, 1_050_000));
    }

    @Test
    void propertyAtThresholdNeedsBaseDocumentsAndTwoRepairEstimates() {
        assertEquals(List.of("claim form", "photo of damage", "two repair estimates"),
                DocumentChecklist.required(ClaimType.PROPERTY, THRESHOLD));
    }

    @ParameterizedTest
    @EnumSource(ClaimType.class)
    void oneCentBelowThresholdDoesNotNeedRepairEstimates(ClaimType type) {
        List<String> docs = DocumentChecklist.required(type, THRESHOLD - 1);
        assertEquals(false, docs.contains("two repair estimates"));
        assertEquals(type == ClaimType.PROPERTY ? 2 : 3, docs.size());
    }

    @ParameterizedTest
    @EnumSource(ClaimType.class)
    void exactlyAtThresholdNeedsRepairEstimatesLast(ClaimType type) {
        List<String> docs = DocumentChecklist.required(type, THRESHOLD);
        assertEquals("two repair estimates", docs.get(docs.size() - 1));
        assertEquals(type == ClaimType.PROPERTY ? 3 : 4, docs.size());
    }

    @ParameterizedTest
    @EnumSource(ClaimType.class)
    void oneCentAboveThresholdNeedsRepairEstimates(ClaimType type) {
        List<String> docs = DocumentChecklist.required(type, THRESHOLD + 1);
        assertEquals("two repair estimates", docs.get(docs.size() - 1));
        assertEquals(type == ClaimType.PROPERTY ? 3 : 4, docs.size());
    }

    @ParameterizedTest
    @EnumSource(ClaimType.class)
    void everyTypeStartsWithClaimFormAndPhoto(ClaimType type) {
        List<String> docs = DocumentChecklist.required(type, 0);
        assertEquals(List.of("claim form", "photo of damage"), docs.subList(0, 2));
    }

    @Test
    void zeroAmountNeedsNoRepairEstimates() {
        assertEquals(List.of("claim form", "photo of damage", "police report"),
                DocumentChecklist.required(ClaimType.THEFT, 0));
    }

    @Test
    void negativeAmountNeedsNoRepairEstimates() {
        assertEquals(List.of("claim form", "photo of damage", "plumber or contractor report"),
                DocumentChecklist.required(ClaimType.WATER_DAMAGE, -1_500_000));
    }

    @Test
    void veryLargeAmountNeedsRepairEstimates() {
        assertEquals(List.of("claim form", "photo of damage", "two repair estimates"),
                DocumentChecklist.required(ClaimType.PROPERTY, Long.MAX_VALUE));
    }

    @Test
    void nullTypeIsRejected() {
        assertThrows(NullPointerException.class, () -> DocumentChecklist.required(null, 500_000));
    }

    @Test
    void eachCallReturnsAFreshListUnaffectedByCallerMutation() {
        List<String> first = DocumentChecklist.required(ClaimType.PROPERTY, 0);
        first.add("extra");
        List<String> second = DocumentChecklist.required(ClaimType.PROPERTY, 0);
        assertNotSame(first, second);
        assertEquals(List.of("claim form", "photo of damage"), second);
    }
}
