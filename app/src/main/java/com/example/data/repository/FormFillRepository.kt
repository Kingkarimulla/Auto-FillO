package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.FormFillDao
import com.example.data.model.AutoFillHistoryEntity
import com.example.data.model.CategoryType
import com.example.data.model.ProfileFieldEntity
import com.example.data.model.UserProfileEntity
import com.example.security.EncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FormFillRepository(context: Context) {

    private val dao: FormFillDao = AppDatabase.getDatabase(context).formFillDao()

    val allProfiles: Flow<List<UserProfileEntity>> = dao.getAllProfiles()
    val allHistory: Flow<List<AutoFillHistoryEntity>> = dao.getAllHistory()

    fun getRecentHistory(limit: Int = 5): Flow<List<AutoFillHistoryEntity>> = dao.getRecentHistory(limit)

    fun getFieldsForProfile(profileId: Long): Flow<List<ProfileFieldEntity>> = dao.getFieldsForProfile(profileId)

    suspend fun getDecryptedFieldsForProfile(profileId: Long): List<DecryptedField> = withContext(Dispatchers.IO) {
        val fields = dao.getFieldsListForProfile(profileId)
        fields.map { field ->
            DecryptedField(
                id = field.id,
                profileId = field.profileId,
                category = field.category,
                fieldKey = field.fieldKey,
                fieldLabel = field.fieldLabel,
                fieldValue = EncryptionManager.decrypt(field.fieldValueEncrypted),
                isSensitive = field.isSensitive
            )
        }
    }

    suspend fun initializeDefaultDataIfNeeded() = withContext(Dispatchers.IO) {
        val defaultProfile = dao.getDefaultProfile()
        if (defaultProfile == null) {
            val profileId = dao.insertProfile(
                UserProfileEntity(profileName = "Personal Profile (Default)", isDefault = true)
            )

            val seedFields = listOf(
                // PRIMARY
                createField(profileId, CategoryType.PRIMARY, "company", "Company Name", "Acme Corporation", false),
                createField(profileId, CategoryType.PRIMARY, "first_name", "First Name", "Rahul", false),
                createField(profileId, CategoryType.PRIMARY, "last_name", "Last Name", "Sharma", false),
                createField(profileId, CategoryType.PRIMARY, "full_name", "Full Name", "Rahul Kumar Sharma", false),
                createField(profileId, CategoryType.PRIMARY, "username", "Username / Login ID", "rahul_sharma", false),
                createField(profileId, CategoryType.PRIMARY, "password", "Password", "P@ssword123!", true),
                createField(profileId, CategoryType.PRIMARY, "dob", "Date of Birth", "15/08/1996", false),
                createField(profileId, CategoryType.PRIMARY, "gender", "Gender", "Male", false),
                createField(profileId, CategoryType.PRIMARY, "mobile", "Mobile Number", "+91 9876543210", false),
                createField(profileId, CategoryType.PRIMARY, "email", "Email Address", "rahul.sharma@example.com", false),
                createField(profileId, CategoryType.PRIMARY, "business_email", "Business Email", "rahul.sharma@acme.com", false),
                createField(profileId, CategoryType.PRIMARY, "aadhaar", "Aadhaar / SSN", "4512 8890 3341", true),
                createField(profileId, CategoryType.PRIMARY, "pan", "PAN / Tax ID", "ABCDE1234F", true),
                createField(profileId, CategoryType.PRIMARY, "passport", "Passport Number", "Z1234567", true),

                // ADDRESS
                createField(profileId, CategoryType.ADDRESS, "address_line_1", "Address Line 1", "Flat 402, Green Valley Heights", false),
                createField(profileId, CategoryType.ADDRESS, "address_line_2", "Address Line 2", "MG Road, Indiranagar", false),
                createField(profileId, CategoryType.ADDRESS, "landmark", "Landmark", "Near Metro Station", false),
                createField(profileId, CategoryType.ADDRESS, "city", "City", "Bengaluru", false),
                createField(profileId, CategoryType.ADDRESS, "state", "State / Province", "Karnataka", false),
                createField(profileId, CategoryType.ADDRESS, "country", "Country", "India", false),
                createField(profileId, CategoryType.ADDRESS, "pincode", "PIN Code / ZIP Code", "560038", false),

                // EDUCATION
                createField(profileId, CategoryType.EDUCATION, "qualification", "Qualification Level", "Bachelor of Technology", false),
                createField(profileId, CategoryType.EDUCATION, "university", "Board / University", "VTU University", false),
                createField(profileId, CategoryType.EDUCATION, "college", "School / College Name", "BMS College of Engineering", false),
                createField(profileId, CategoryType.EDUCATION, "passing_year", "Year of Passing", "2018", false),
                createField(profileId, CategoryType.EDUCATION, "grade", "Percentage / CGPA", "8.6 CGPA", false),
                createField(profileId, CategoryType.EDUCATION, "roll_no", "Roll / Registration Number", "1BM14CS082", false),

                // BANK
                createField(profileId, CategoryType.BANK, "holder_name", "Account Holder Name", "Rahul Kumar Sharma", false),
                createField(profileId, CategoryType.BANK, "bank_name", "Bank Name", "HDFC Bank", false),
                createField(profileId, CategoryType.BANK, "account_no", "Account Number", "50100234567890", true),
                createField(profileId, CategoryType.BANK, "ifsc", "IFSC / SWIFT Code", "HDFC0001234", false),
                createField(profileId, CategoryType.BANK, "branch", "Branch Name", "Indiranagar Branch", false),
                createField(profileId, CategoryType.BANK, "upi_id", "UPI ID", "rahul@hdfcbank", false),

                // EMPLOYMENT
                createField(profileId, CategoryType.EMPLOYMENT, "emp_status", "Employment Status", "Employed", false),
                createField(profileId, CategoryType.EMPLOYMENT, "company", "Company Name", "TechSolutions Pvt Ltd", false),
                createField(profileId, CategoryType.EMPLOYMENT, "designation", "Designation", "Senior Software Engineer", false),
                createField(profileId, CategoryType.EMPLOYMENT, "emp_id", "Employee ID", "EMP-9042", false),
                createField(profileId, CategoryType.EMPLOYMENT, "experience", "Work Experience", "5 Years 6 Months", false),

                // FAMILY
                createField(profileId, CategoryType.FAMILY, "father_name", "Father's Name", "Suresh Sharma", false),
                createField(profileId, CategoryType.FAMILY, "mother_name", "Mother's Name", "Sunita Sharma", false),
                createField(profileId, CategoryType.FAMILY, "marital_status", "Marital Status", "Single", false),
                createField(profileId, CategoryType.FAMILY, "emergency_contact", "Emergency Contact", "Suresh Sharma (+91 9845012345)", false),

                // GOVERNMENT
                createField(profileId, CategoryType.GOVERNMENT, "caste", "Category / Caste", "General", false),
                createField(profileId, CategoryType.GOVERNMENT, "religion", "Religion", "Hinduism", false),
                createField(profileId, CategoryType.GOVERNMENT, "nationality", "Nationality", "Indian", false),
                createField(profileId, CategoryType.GOVERNMENT, "voter_id", "Voter ID / EPIC", "ABC9876543", true),
                createField(profileId, CategoryType.GOVERNMENT, "dl_number", "Driving License", "KA0320180012345", true),

                // CUSTOM
                createField(profileId, CategoryType.CUSTOM, "insurance_policy", "Health Insurance Policy No", "POL-8877123", false)
            )

            dao.insertFields(seedFields)

            // Seed initial history
            dao.insertHistory(AutoFillHistoryEntity(appName = "SBI Bank Application", fieldLabel = "IFSC Code", categoryName = "Bank Details", fieldValueMasked = "HDFC****234"))
            dao.insertHistory(AutoFillHistoryEntity(appName = "Govt Portal SSC Recruitment", fieldLabel = "Aadhaar Number", categoryName = "Primary Details", fieldValueMasked = "4512****3341"))
            dao.insertHistory(AutoFillHistoryEntity(appName = "Amazon Checkout", fieldLabel = "Delivery Address", categoryName = "Address Details", fieldValueMasked = "Flat****eights"))
        }
    }

    suspend fun saveField(profileId: Long, category: CategoryType, key: String, label: String, value: String, isSensitive: Boolean) = withContext(Dispatchers.IO) {
        val encrypted = EncryptionManager.encrypt(value)
        dao.insertField(
            ProfileFieldEntity(
                profileId = profileId,
                category = category,
                fieldKey = key,
                fieldLabel = label,
                fieldValueEncrypted = encrypted,
                isSensitive = isSensitive
            )
        )
    }

    suspend fun updateField(id: Long, profileId: Long, category: CategoryType, key: String, label: String, value: String, isSensitive: Boolean) = withContext(Dispatchers.IO) {
        val encrypted = EncryptionManager.encrypt(value)
        dao.updateField(
            ProfileFieldEntity(
                id = id,
                profileId = profileId,
                category = category,
                fieldKey = key,
                fieldLabel = label,
                fieldValueEncrypted = encrypted,
                isSensitive = isSensitive
            )
        )
    }

    suspend fun deleteField(fieldId: Long) = withContext(Dispatchers.IO) {
        dao.deleteField(fieldId)
    }

    suspend fun addProfile(name: String) = withContext(Dispatchers.IO) {
        dao.insertProfile(UserProfileEntity(profileName = name, isDefault = false))
    }

    suspend fun setDefaultProfile(profileId: Long) = withContext(Dispatchers.IO) {
        dao.clearDefaultProfiles()
        dao.setDefaultProfile(profileId)
    }

    suspend fun deleteProfile(profileId: Long) = withContext(Dispatchers.IO) {
        dao.deleteFieldsForProfile(profileId)
        dao.deleteProfile(profileId)
    }

    suspend fun logAutoFillEvent(appName: String, fieldLabel: String, categoryName: String, value: String, isSuccess: Boolean = true) = withContext(Dispatchers.IO) {
        val masked = EncryptionManager.maskValue(value)
        dao.insertHistory(
            AutoFillHistoryEntity(
                appName = appName,
                fieldLabel = fieldLabel,
                categoryName = categoryName,
                fieldValueMasked = masked,
                isSuccess = isSuccess
            )
        )
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearHistory()
    }

    private fun createField(
        profileId: Long,
        category: CategoryType,
        key: String,
        label: String,
        value: String,
        isSensitive: Boolean
    ): ProfileFieldEntity {
        return ProfileFieldEntity(
            profileId = profileId,
            category = category,
            fieldKey = key,
            fieldLabel = label,
            fieldValueEncrypted = EncryptionManager.encrypt(value),
            isSensitive = isSensitive
        )
    }
}

data class DecryptedField(
    val id: Long,
    val profileId: Long,
    val category: CategoryType,
    val fieldKey: String,
    val fieldLabel: String,
    val fieldValue: String,
    val isSensitive: Boolean
)
