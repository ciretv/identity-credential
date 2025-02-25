/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.identity.documenttype.knowntypes

import com.android.identity.cbor.toDataItem
import com.android.identity.cbor.toDataItemFullDate
import com.android.identity.documenttype.DocumentAttributeType
import com.android.identity.documenttype.DocumentType
import com.android.identity.documenttype.Icon
import com.android.identity.documenttype.knowntypes.DrivingLicense.MDL_NAMESPACE
import com.android.identity.util.fromBase64Url
import kotlinx.datetime.LocalDate

/**
 * Object containing the metadata of the EU Personal ID Document Type.
 *
 * Source: https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework/blob/main/docs/annexes/annex-06-pid-rulebook.md
 */
object EUPersonalID {
    const val EUPID_DOCTYPE = "eu.europa.ec.eudi.pid.1"
    const val EUPID_NAMESPACE = "eu.europa.ec.eudi.pid.1"
    const val EUPID_VCT = "urn:eu.europa.ec.eudi:pid:1"

    /**
     * Build the EU Personal ID Document Type.
     */
    fun getDocumentType(): DocumentType {
        return DocumentType.Builder("EU Personal ID")
            .addMdocDocumentType(EUPID_DOCTYPE)
            .addVcDocumentType(EUPID_VCT)

            // Attributes found in SampleData for 2025 RDW test event
            .addAttribute(
                DocumentAttributeType.String,
                "family_name",
                "Family Name",
                "Current last name(s), surname(s), or primary identifier of the PID holder",
                true,
                EUPID_NAMESPACE,
                Icon.PERSON,
                RDW_test_event_SampleData.FAMILY_NAME.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "given_name",
                "Given Names",
                "Current first name(s), other name(s), or secondary identifier of the PID holder",
                true,
                EUPID_NAMESPACE,
                Icon.PERSON,
                RDW_test_event_SampleData.GIVEN_NAME.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Date,
                "birth_date",
                "Date of Birth",
                "Day, month, and year on which the PID holder was born. If unknown, approximate date of birth.",
                true,
                EUPID_NAMESPACE,
                Icon.TODAY,
                LocalDate.parse(RDW_test_event_SampleData.BIRTH_DATE).toDataItemFullDate()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "birth_place",
                "Place of Birth",
                "Country and municipality or state/province where the PID holder was born",
                false,
                EUPID_NAMESPACE,
                Icon.PLACE,
                RDW_test_event_SampleData.BIRTH_PLACE.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "nationality",
                "Nationality",
                "Alpha-2 country code as specified in ISO 3166-1, representing the nationality of the PID User.",
                true,
                EUPID_NAMESPACE,
                Icon.LANGUAGE,
                RDW_test_event_SampleData.NATIONALITY.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "resident_address",
                "Resident Address",
                "The full address of the place where the PID holder currently resides and/or may be contacted",
                false,
                EUPID_NAMESPACE,
                Icon.PLACE,
                RDW_test_event_SampleData.RESIDENT_ADDRESS.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "resident_country",
                "Resident Country",
                "The country where the PID User currently resides",
                false,
                EUPID_NAMESPACE,
                Icon.PLACE,
                RDW_test_event_SampleData.RESIDENT_COUNTRY.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "resident_state",
                "Resident State",
                "The state, province, district, or local area where the PID User currently resides.",
                false,
                EUPID_NAMESPACE,
                Icon.PLACE,
                RDW_test_event_SampleData.RESIDENT_STATE.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "resident_city",
                "Resident City",
                "The city where the PID holder currently resides",
                false,
                EUPID_NAMESPACE,
                Icon.PLACE,
                RDW_test_event_SampleData.RESIDENT_CITY.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "resident_postal_code",
                "Resident Postal Code",
                "The postal code of the place where the PID holder currently resides",
                false,
                EUPID_NAMESPACE,
                Icon.PLACE,
                RDW_test_event_SampleData.RESIDENT_POSTAL_CODE.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "resident_street",
                "Resident Street",
                "The name of the street where the PID User currently resides.",
                false,
                EUPID_NAMESPACE,
                Icon.PLACE,
                RDW_test_event_SampleData.RESIDENT_STREET.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "resident_house_number",
                "Resident House Number",
                "The house number where the PID User currently resides, including any affix or suffix",
                false,
                EUPID_NAMESPACE,
                Icon.PLACE,
                RDW_test_event_SampleData.RESIDENT_HOUSE_NUMBER.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "personal_administrative_number",
                "Personal Administrative Number",
                "A number assigned by the PID Provider for audit control or other purposes.",
                false,
                EUPID_NAMESPACE,
                Icon.NUMBERS,
                RDW_test_event_SampleData.PERSONAL_ADMINISTRATIVE_NUMBER.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Picture,
                "portrait",
                "Photo of Holder",
                "A reproduction of the mDL holder’s portrait.",
                true,
                EUPID_NAMESPACE,
                Icon.ACCOUNT_BOX,
                RDW_test_event_SampleData.PORTRAIT_BASE64URL.fromBase64Url().toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Date,
                "portrait_capture_date",
                "Portrait Image Timestamp",
                "Date when portrait was taken",
                false,
                EUPID_NAMESPACE,
                Icon.TODAY,
                LocalDate.parse(RDW_test_event_SampleData.PORTRAIT_CAPTURE_DATE).toDataItemFullDate()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "family_name_birth",
                "Family Name at Birth",
                "Last name(s), surname(s), or primary identifier of the PID holder at birth",
                false,
                EUPID_NAMESPACE,
                Icon.PERSON,
                RDW_test_event_SampleData.FAMILY_NAME_BIRTH.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "given_name_birth",
                "First Name at Birth",
                "First name(s), other name(s), or secondary identifier of the PID holder at birth",
                false,
                EUPID_NAMESPACE,
                Icon.PERSON,
                RDW_test_event_SampleData.GIVEN_NAME_BIRTH.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.IntegerOptions(Options.SEX_ISO_IEC_5218),
                "sex",
                "Sex",
                "PID holder’s gender",
                false,
                EUPID_NAMESPACE,
                Icon.EMERGENCY,
                RDW_test_event_SampleData.SEX_ISO218.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "email_address",
                "Email Address",
                "PID holder’s email address",
                false,
                EUPID_NAMESPACE,
                Icon.EMERGENCY,
                RDW_test_event_SampleData.EMAIL_ADDRESS.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "mobile_phone_number",
                "Mobile Phone Number",
                "PID holder’s mobile phone number",
                false,
                EUPID_NAMESPACE,
                Icon.EMERGENCY,
                RDW_test_event_SampleData.MOBILE_PHONE_NUMBER.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Date,
                "expiry_date",
                "Date of Expiry",
                "Date (and possibly time) when the PID will expire.",
                true,
                EUPID_NAMESPACE,
                Icon.CALENDAR_CLOCK,
                LocalDate.parse(RDW_test_event_SampleData.EXPIRY_DATE).toDataItemFullDate()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "issuing_authority",
                "Issuing Authority",
                "Name of the administrative authority that has issued this PID instance.",
                true,
                EUPID_NAMESPACE,
                Icon.ACCOUNT_BALANCE,
                RDW_test_event_SampleData.ISSUING_AUTHORITY_EU_PID.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "issuing_country",
                "Issuing Country",
                "Alpha-2 country code, as defined in ISO 3166-1, of the issuing authority’s country or territory",
                true,
                EUPID_NAMESPACE,
                Icon.ACCOUNT_BALANCE,
                RDW_test_event_SampleData.ISSUING_COUNTRY.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.String,
                "document_number",
                "Document Number",
                "A number for the PID, assigned by the PID Provider.",
                false,
                EUPID_NAMESPACE,
                Icon.NUMBERS,
                RDW_test_event_SampleData.DOCUMENT_NUMBER.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Date,
                "issuance_date",
                "Date of Issue",
                "Date (and possibly time) when the PID was issued.",
                true,
                EUPID_NAMESPACE,
                Icon.DATE_RANGE,
                LocalDate.parse(RDW_test_event_SampleData.ISSUANCE_DATE).toDataItemFullDate()
            )
            .addAttribute(
                DocumentAttributeType.Boolean,
                "age_over_18",
                "Older Than 18",
                "Age over 18?",
                false,
                EUPID_NAMESPACE,
                Icon.TODAY,
                RDW_test_event_SampleData.AGE_OVER_18.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Number,
                "age_in_years",
                "Age in Years",
                "The age of the PID holder in years",
                false,
                EUPID_NAMESPACE,
                Icon.TODAY,
                RDW_test_event_SampleData.AGE_IN_YEARS.toDataItem()
            )
            .addAttribute(
                DocumentAttributeType.Number,
                "age_birth_year",
                "Year of Birth",
                "The year when the PID holder was born",
                false,
                EUPID_NAMESPACE,
                Icon.TODAY,
                RDW_test_event_SampleData.AGE_BIRTH_YEAR.toDataItem()
            )
    .addSampleRequest(
                id = "mandatory",
                displayName = "Mandatory Data Elements",
                mdocDataElements = mapOf(
                    EUPID_NAMESPACE to mapOf(
                        "family_name" to false,
                        "given_name" to false,
                        "birth_date" to false,
                        "age_over_18" to false,
                        "issuance_date" to false,
                        "expiry_date" to false,
                        "issuing_authority" to false,
                        "issuing_country" to false
                    )
                ),
                vcClaims = listOf(
                    "family_name",
                    "given_name",
                    "birth_date",
                    "age_over_18",
                    "issuance_date",
                    "expiry_date",
                    "issuing_authority",
                    "issuing_country"
                )
            )
            .addSampleRequest(
                id = "full",
                displayName = "All Data Elements",
                mdocDataElements = mapOf(
                    EUPID_NAMESPACE to mapOf()
                ),
                vcClaims = listOf()
            )
            .build()
    }
}