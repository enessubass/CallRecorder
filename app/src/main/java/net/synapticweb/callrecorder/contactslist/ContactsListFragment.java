package net.synapticweb.callrecorder.contactslist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import net.synapticweb.callrecorder.contactdetail.ContactDetailActivity;
import net.synapticweb.callrecorder.data.Contact;
import net.synapticweb.callrecorder.R;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ContactsListFragment extends Fragment implements ContactsListContract.View {
    private ContactsListPresenter presenter;
    private ContactsAdapter adapter;
    private RecyclerView contactsRecycler;
    private int currentPos = 0;
    private Long newAddedContactId = null;
    private boolean hasRestarted = false;
    private AppCompatActivity parentActivity;
    private final static String CURRENT_POS_KEY = "current_pos";
    public final static String ARG_CONTACT = "arg_contact";

    @Override
    public Activity getParentActivity() {
        return parentActivity;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        parentActivity = (AppCompatActivity) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        parentActivity = null;
    }

    @Override
    public RecyclerView getContactsRecycler() {
        return contactsRecycler;
    }

    @Override
    public ContactsAdapter getContactsAdapter() {
        return adapter;
    }

    @Override
    public void setNewAddedContactId(long id) {
        newAddedContactId = id;
    }

    public void resetDetailFragment() {
        currentPos = 0;
    }

    @Override
    public void onResume(){
        super.onResume();
        presenter.loadContacts();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(CURRENT_POS_KEY, currentPos);
    }

    @Override
    public void startContactDetailActivity(Contact contact) {
        Intent detailIntent = new Intent(getContext(), ContactDetailActivity.class);
        detailIntent.putExtra(ARG_CONTACT, contact);
        startActivity(detailIntent);
    }

    public boolean isSinglePaneLayout() {
        return (parentActivity != null &&
                parentActivity.findViewById(R.id.contact_detail_fragment_container) == null);
    }

    //e apelată de callback-ul pasat funcției ContactRepository::getContacts(), la rîndul ei apelată de
    // ContactsListPresenter::loadContacts()
    @Override
    public void showContacts(List<Contact> contacts) {
        adapter = new ContactsAdapter(contacts);
        if(newAddedContactId != null) {
            for(Contact contact : adapter.getData())
                if(contact.getId() == newAddedContactId) {
                    currentPos = adapter.getData().indexOf(contact);
                    break;
                }
            newAddedContactId = null;
        }
        //înainte de asta aveam un adapter.replaceData() care înlocuia lista din adapter și apela notifyData
        //setChanged(). Performanța era mai bună dar problema era că la adăugarea unui contact nou rămînea
        //marcat și cel anterior.
        contactsRecycler.setAdapter(adapter);
        adapter.notifyDataSetChanged();
        if(!isSinglePaneLayout() && !hasRestarted) { //dacă ne găsim după un restart al activității
            // datorat rotirii nu trebuie să mai înlocuim fragmentul detaliu, el este deja acolo. Dacă
            // îl înlocuim onResume() al fragmnetului detaliu va fi apelat de 2 ori: prima dată din cauza
            // restartului fragmentului detaliu - cu datele de stare, a doua oară datorită înlocuirii - fără
            //datele de stare.
            if(adapter.getItemCount() > 0)
                presenter.setCurrentDetail(adapter.getItem(currentPos));
            else
                presenter.setCurrentDetail(null);
        }
        TextView noContent = parentActivity.findViewById(R.id.no_content);
        if(adapter.getItemCount() > 0)
            noContent.setVisibility(View.GONE);
        else
            noContent.setVisibility(View.VISIBLE);
    }

    @Override
    public void selectContact(View contactSlot) {
        if(contactSlot != null)
            contactSlot.findViewById(R.id.tablet_current_selection).setVisibility(View.VISIBLE);
    }

    @Override
    public void deselectContact(View contactSlot) {
        if(contactSlot != null)
            contactSlot.findViewById(R.id.tablet_current_selection).setVisibility(View.GONE);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            currentPos = savedInstanceState.getInt(CURRENT_POS_KEY);
            hasRestarted = true;
        }
        this.adapter = new ContactsAdapter(new ArrayList<Contact>(0));
        this.presenter = new ContactsListPresenter(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        contactsRecycler = (RecyclerView) inflater.inflate(R.layout.list_contacts_fragment, container, false);
        contactsRecycler.setLayoutManager(new LinearLayoutManager(parentActivity));

        if(parentActivity != null) {
            FloatingActionButton fab = parentActivity.findViewById(R.id.add_numbers);
            fab.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    presenter.addNewContact();
                }
            });
        }
        return contactsRecycler;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if(resultCode == Activity.RESULT_OK && requestCode == ContactsListPresenter.REQUEST_ADD_CONTACT)
            presenter.onAddContactResult(intent);
    }

    public class ContactHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        ImageView contactPhoto;
        TextView mContactName;
        TextView mPhoneNumber;
        Contact contact;

        ContactHolder(LayoutInflater inflater, ViewGroup parent)
        {
            super(inflater.inflate(R.layout.listened_phone, parent, false));
            itemView.setOnClickListener(this);
            contactPhoto = itemView.findViewById(R.id.contact_photo);
            mContactName = itemView.findViewById(R.id.contact_name);
            mPhoneNumber = itemView.findViewById(R.id.phone_number);
        }

        @Override
        public void onClick(View view) {
            int previousPos = currentPos;
            currentPos = getAdapterPosition();
            presenter.manageContactDetails(contact, previousPos, currentPos);
        }
    }

    class ContactsAdapter extends RecyclerView.Adapter<ContactHolder> {
        private List<Contact> contacts;
        ContactsAdapter(List<Contact> list){
            contacts = list;
        }

        List<Contact> getData() {
            return contacts;
        }

        @Override
        @NonNull
        public ContactHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(parentActivity);
            return new ContactHolder(layoutInflater, parent);
        }

        @Override
        public void onBindViewHolder(@NonNull ContactHolder holder, int position) {
            Contact contact = contacts.get(position);

            if(!isSinglePaneLayout())
                holder.contactPhoto.setVisibility(View.GONE);
            else {
                if (contact.getPhotoUri() != null) {
                    holder.contactPhoto.setImageURI(null); //cînd se schimbă succesiv 2 poze făcute de cameră se folosește același fișier și optimizările android fac necesar acest hack pentru a obține refresh-ul pozei
                    holder.contactPhoto.setImageURI(contact.getPhotoUri());
                } else {
                    if (contact.isPrivateNumber()) {
                        holder.contactPhoto.setImageResource(R.drawable.user_contact_red);
                        holder.mPhoneNumber.setVisibility(View.GONE);
                    }
                    else
                        holder.contactPhoto.setImageResource(R.drawable.user_contact);
                }
            }

            holder.mContactName.setText(contact.getContactName());
            holder.contact = contact;
            if(!contact.isPrivateNumber())
                holder.mPhoneNumber.setText(contact.getPhoneNumber());
            if(!isSinglePaneLayout())
                holder.mPhoneNumber.setVisibility(View.GONE);

            if(!isSinglePaneLayout() && position == currentPos)
                selectContact(holder.itemView);
            else if(!isSinglePaneLayout() && position != currentPos)
                deselectContact(holder.itemView);
         }

        Contact getItem(int position) {
            return contacts.get(position);
        }

        @Override
        public int getItemCount() {
            return contacts.size();
        }

    }
}