package com.example.sms;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION_CODE = 123;
    private static final int CONTACTS_PERMISSION_CODE = 124;
    private static final int PICK_CONTACT_REQUEST = 1;
    private EditText phoneNumberEditText;
    private EditText message;
    private List<String> contactNames;
    private Spinner contactSpinner;
    private int day;
    private int month;
    private int year;
    private int hour;
    private int minute;
    private TimePicker timePicker;
    private DatePicker datePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        contactNames = new ArrayList<>();
        
        message = findViewById(R.id.editTextMessage);
        timePicker = findViewById(R.id.timePicker);
        datePicker = findViewById(R.id.datePicker);

        // Request the SEND_SMS permission at runtime if necessary
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.SEND_SMS }, SMS_PERMISSION_CODE);
        }

        Cursor cursor = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            do {
                @SuppressLint("Range")
                String contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                contactNames.add(contactName);
            } while (cursor.moveToNext());
            cursor.close();
        }

        // Request the READ_CONTACTS permission at runtime if necessary
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.READ_CONTACTS }, CONTACTS_PERMISSION_CODE);
        }

        // Populate the Spinner with the list of contact names
        contactSpinner = findViewById(R.id.contact_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, contactNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        contactSpinner.setAdapter(adapter);
    }

    @SuppressLint("Range")
    public void sendSMS(View view) {
        String messageToSend = message.getText().toString();
        day = datePicker.getDayOfMonth();
        month = datePicker.getMonth();
        year = datePicker.getYear();
        hour = timePicker.getCurrentHour();
        minute = timePicker.getCurrentMinute();

        // Get the selected contact from the spinner
        String selectedContactName = (String) contactSpinner.getSelectedItem();
        String number = null;

        // Query the Contacts content provider to retrieve the phone number of the selected contact
        Cursor cursor = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, null,
                ContactsContract.Contacts.DISPLAY_NAME + " = ?", new String[]{selectedContactName}, null);
        if (cursor != null && cursor.moveToFirst()) {
            String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
            Cursor phoneCursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{contactId}, null);
            if (phoneCursor != null && phoneCursor.moveToFirst()) {
                number = phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
            }
            phoneCursor.close();
        }
        cursor.close();

        // Get the time in milliseconds for the selected date and time
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, day, hour, minute);
        long timeInMillis = calendar.getTimeInMillis();

        // Schedule the SMS using the obtained phone number and message
        Intent intent = new Intent(getApplicationContext(), SmsBroadcastReceiver.class);
        intent.putExtra("phone", number);
        intent.putExtra("message", messageToSend);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, intent,
                PendingIntent.FLAG_MUTABLE);

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.set(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);

        Toast.makeText(getApplicationContext(),
                "Message scheduled for " + day + "/" + (month + 1) + "/" + year + " at " + hour + ":" + minute,
                Toast.LENGTH_LONG).show();
    }

    public void pickContact(View view) {
        Intent pickContactIntent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        startActivityForResult(pickContactIntent, PICK_CONTACT_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "SMS permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "SMS permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == CONTACTS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "Contacts permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Contacts permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK) {
            Uri contactUri = data.getData();

            String[] projection = { ContactsContract.CommonDataKinds.Phone.NUMBER };
            Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                String number = cursor.getString(numberIndex);

                phoneNumberEditText.setText(number);
            }

            if (cursor != null) {
                cursor.close();
            }
        }
    }
}