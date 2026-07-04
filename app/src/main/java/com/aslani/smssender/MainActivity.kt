package com.aslani.smssender

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.aslani.smssender.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val contacts = mutableListOf<Contact>()
    private lateinit var adapter: ContactAdapter

    // فاصله زمانی بین هر پیامک (میلی‌ثانیه) - برای جلوگیری از بلاک شدن توسط اپراتور
    private val delayBetweenSmsMs = 3000L

    private val pickCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { loadCsv(it) }
    }

    private val requestSmsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSendingProcess()
        } else {
            Toast.makeText(this, "برای ارسال پیامک باید دسترسی SMS را تایید کنید", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ContactAdapter(contacts)
        binding.rvContacts.layoutManager = LinearLayoutManager(this)
        binding.rvContacts.adapter = adapter

        binding.btnImportCsv.setOnClickListener {
            pickCsvLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "*/*"))
        }

        binding.btnSend.setOnClickListener {
            onSendClicked()
        }
    }

    private fun loadCsv(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val newContacts = mutableListOf<Contact>()

            reader.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty()) {
                    val parts = line.split(",")
                    if (parts.size >= 2) {
                        val name = parts[0].trim().trim('"')
                        val phone = normalizePhone(parts[1].trim().trim('"'))
                        if (phone.isNotEmpty()) {
                            newContacts.add(Contact(name, phone))
                        }
                    }
                }
            }
            reader.close()

            contacts.clear()
            contacts.addAll(newContacts)
            adapter.setContacts(contacts)
            binding.tvContactsCount.text = "تعداد مخاطبین بارگذاری‌شده: ${contacts.size}"

            if (contacts.isEmpty()) {
                Toast.makeText(this, "هیچ مخاطبی پیدا نشد. فرمت فایل را چک کنید (نام,شماره)", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در خواندن فایل: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun normalizePhone(raw: String): String {
        // تبدیل اعداد فارسی به انگلیسی و حذف کاراکترهای اضافه
        val persianToEnglish = mapOf(
            '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
            '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9'
        )
        val converted = raw.map { persianToEnglish[it] ?: it }.joinToString("")
        return converted.filter { it.isDigit() || it == '+' }
    }

    private fun onSendClicked() {
        if (contacts.isEmpty()) {
            Toast.makeText(this, "اول لیست مخاطبین را بارگذاری کنید", Toast.LENGTH_SHORT).show()
            return
        }
        val template = binding.etTemplate.text.toString().trim()
        if (template.isEmpty()) {
            Toast.makeText(this, "متن پیامک را وارد کنید", Toast.LENGTH_SHORT).show()
            return
        }
        if (!template.contains("{name}")) {
            Toast.makeText(this, "توجه: در متن پیامک {name} پیدا نشد، اسم افراد درج نمی‌شود", Toast.LENGTH_LONG).show()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestSmsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        } else {
            startSendingProcess()
        }
    }

    private fun startSendingProcess() {
        val template = binding.etTemplate.text.toString().trim()
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.progressBar.max = contacts.size
        binding.progressBar.progress = 0
        binding.btnSend.isEnabled = false

        val handler = Handler(Looper.getMainLooper())

        thread {
            val smsManager = SmsManager.getDefault()

            for (i in contacts.indices) {
                val contact = contacts[i]
                val personalMessage = template.replace("{name}", contact.name)

                try {
                    val parts = smsManager.divideMessage(personalMessage)
                    if (parts.size > 1) {
                        smsManager.sendMultipartTextMessage(
                            contact.phone, null, parts, null, null
                        )
                    } else {
                        smsManager.sendTextMessage(
                            contact.phone, null, personalMessage, null, null
                        )
                    }
                    handler.post {
                        adapter.updateStatus(i, "ارسال شد ✓")
                        binding.progressBar.progress = i + 1
                        binding.tvStatus.text = "در حال ارسال... (${i + 1}/${contacts.size})"
                    }
                } catch (e: Exception) {
                    handler.post {
                        adapter.updateStatus(i, "خطا: ${e.message}")
                        binding.progressBar.progress = i + 1
                    }
                }

                Thread.sleep(delayBetweenSmsMs)
            }

            handler.post {
                binding.btnSend.isEnabled = true
                binding.tvStatus.text = "ارسال به همه مخاطبین تمام شد ✓"
                Toast.makeText(this, "ارسال پیامک‌ها به پایان رسید", Toast.LENGTH_LONG).show()
            }
        }
    }
}
