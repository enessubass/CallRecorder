package net.synapticweb.callrecorder.contactdetail;

import android.content.Intent;
import android.os.Bundle;


import net.synapticweb.callrecorder.R;
import net.synapticweb.callrecorder.TemplateActivity;
import net.synapticweb.callrecorder.contactslist.ContactsListFragment;
import net.synapticweb.callrecorder.data.Contact;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

public class ContactDetailActivity extends TemplateActivity {
    Contact contact;

    @Override
    protected Fragment createFragment() {
        return ContactDetailFragment.newInstance(contact);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkIfThemeChanged();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme();
        setContentView(R.layout.contact_detail_activity);
        Intent intent = getIntent();
        contact = intent.getParcelableExtra(ContactsListFragment.ARG_CONTACT);

        insertFragment(R.id.contact_detail_fragment_container);

        Toolbar toolbar = findViewById(R.id.toolbar_detail);
        toolbar.setTitle(contact.getContactName());
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);
    }

}