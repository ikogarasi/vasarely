package com.example.vasarely.model

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.vasarely.SingleLiveEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.File
import kotlin.collections.HashMap

class Database {

    private lateinit var application: Application
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var databaseReference: DatabaseReference
    private lateinit var preferencesReference: DatabaseReference
    private lateinit var currentUser: FirebaseUser
    private lateinit var currentUserDb: DatabaseReference

    private lateinit var firebaseStore: FirebaseStorage
    private lateinit var storageReference: StorageReference
    private lateinit var imageReference: StorageReference

    private var amountOfWorks = 0
    private lateinit var uid: String

    lateinit var userMutableLiveData: SingleLiveEvent<Boolean>
    lateinit var userData: SingleLiveEvent<Any>
    lateinit var allUserPosts: SingleLiveEvent<List<Bitmap>>
    lateinit var recommendationsToProcess: SingleLiveEvent<List<Map<Int, Any?>>>
    lateinit var dataChangeExceptions: SingleLiveEvent<String>

    lateinit var recommendation: SingleLiveEvent<Bitmap>


    fun initDatabase(application: Application) {
        this.application = application

        firebaseAuth = FirebaseAuth.getInstance()

        firebaseStore = FirebaseStorage.getInstance()
        storageReference = firebaseStore.reference

        userMutableLiveData = SingleLiveEvent()
        userData = SingleLiveEvent()
        allUserPosts = SingleLiveEvent()
        recommendationsToProcess = SingleLiveEvent()
        dataChangeExceptions = SingleLiveEvent()

        recommendation = SingleLiveEvent()
    }

    fun register(email: String, password: String, username: String) {
        firebaseAuth.createUserWithEmailAndPassword(email, password).addOnSuccessListener {
            if (firebaseAuth.currentUser != null) {
                userMutableLiveData.postValue(false)

                firebaseDatabase = FirebaseDatabase.getInstance("https://vasarely-f0ed5-default-rtdb.europe-west1.firebasedatabase.app")
                databaseReference = firebaseDatabase.reference.child("profiles")
                currentUser = firebaseAuth.currentUser!!
                currentUserDb = databaseReference.child((currentUser.uid))
                uid = currentUser.uid
                currentUserDb.child("userData").child("username").setValue(username)
                currentUserDb.child("userData").child("worksAmount").setValue(0)
                currentUserDb.child("profileData").child("posts").setValue(1)
            }
        }.addOnFailureListener { exception ->
            dataChangeExceptions.postValue(exception.toString())
        }
    }

    fun login(email: String, password: String) {
        firebaseAuth.signInWithEmailAndPassword(email, password).addOnSuccessListener{
            if(firebaseAuth.currentUser != null) {

                firebaseDatabase = FirebaseDatabase.getInstance("https://vasarely-f0ed5-default-rtdb.europe-west1.firebasedatabase.app")
                databaseReference = firebaseDatabase.reference.child("profiles")
                currentUser = firebaseAuth.currentUser!!
                currentUserDb = databaseReference.child((currentUser.uid))
                uid = currentUser.uid

                currentUserDb.child("userData").child("preferences").get().addOnSuccessListener {
                    if (!it.exists()) {
                        userMutableLiveData.postValue(false)
                    }
                    else {
                        userMutableLiveData.postValue(true)
                    }
                }
            }
        }.addOnFailureListener { exception ->
            dataChangeExceptions.postValue(exception.toString())
        }
    }


    fun logout() {
        firebaseAuth.signOut()
    }

    fun savePreference(technique : String, mood : String, selectedGenres : List<String>) {

        preferencesReference = currentUserDb.child("userData").child("preferences")

        val techniqueReference = preferencesReference.child("technique")
        val moodReference = preferencesReference.child("mood")
        val genresReference = preferencesReference.child("genres")

        techniqueReference.setValue(technique).addOnFailureListener { exception ->
            dataChangeExceptions.postValue(exception.toString())
        }

        moodReference.setValue(mood).addOnFailureListener { exception ->
            dataChangeExceptions.postValue(exception.toString())
        }

        genresReference.setValue(selectedGenres).addOnFailureListener { exception ->
            dataChangeExceptions.postValue(exception.toString())
        }
    }

    fun updateName(newNickname: String) {
        currentUserDb.child("username").setValue(newNickname).addOnFailureListener { exception ->
            dataChangeExceptions.postValue(exception.toString())
        }
    }

    fun recommendationsSearch() {
        databaseReference.get().addOnSuccessListener {
            val recommendationsToProcessList = mutableListOf<Map<Int, Any?>>()
            for (snapshot in it.children) {
                if (snapshot.key != uid) {
                    val postsData = mutableMapOf<Int, Any?>()
                    for ((dataIndex, data) in snapshot.children.withIndex()) {
                        if (dataIndex == 0) {
                            for (post in data.children) {
                                for (postNumberAndTags in post.children) {
                                    val postNumberAndTagsHashMap = postNumberAndTags.value as HashMap<*, *>
                                    val postTagsList = postNumberAndTagsHashMap.values
                                    val tags = mutableListOf<String>()
                                    for ((jIndex, j) in postTagsList.withIndex()) {
                                        if (jIndex == 0) {
                                            j as HashMap<*, *>
                                            tags.add(j["mood"].toString())
                                            tags.add(j["genre"].toString())
                                            tags.add(j["technique"].toString())
                                        }
                                        else {
                                            tags.add(j as String)
                                            tags.add(snapshot.key as String)
                                        }
                                    }
                                    postsData[postNumberAndTags.key!!.toInt()] = tags
                                }
                            }
                        }

                        else {
                            for (child in data.children) {
                                if (child.key == "worksAmount" && child.value != 0L) {
                                    recommendationsToProcessList.add(postsData)
                                    Log.d("Posts", postsData.toString())
                                }
                            }
                        }
                    }
                }
            }

            recommendationsToProcess.postValue(recommendationsToProcessList)

        } .addOnFailureListener { exception ->
            dataChangeExceptions.postValue(exception.toString())
        }
    }

    fun getImage(uid : String, postNumber : String) {
        val localFile = File.createTempFile("tempImage", "jpg")
        Log.d("postNumber", postNumber)
        val p = postNumber.toInt()
        imageReference = storageReference.child("uploads/$uid/$p")
        imageReference.getFile(localFile).addOnSuccessListener {
            val bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
            recommendation.postValue(bitmap)
            Log.d("bitmap",bitmap.toString())
        }
    }

    fun getData() {
        currentUserDb.child("userData").get().addOnSuccessListener {
            userData.postValue(it.value)

            val dataSnapshot = it.value as HashMap<*, *>

            amountOfWorks = if (dataSnapshot["worksAmount"] != null)
                dataSnapshot["worksAmount"].toString().toInt()
            else
                0

            val allUserPostsList = mutableListOf<Bitmap>()

            for (i in 1..amountOfWorks) {
                Log.d("i", i.toString())
                val localFile = File.createTempFile("tempImage", "jpg")
                imageReference = storageReference.child("uploads/$uid/$i")
                imageReference.getFile(localFile).addOnSuccessListener {
                    val bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
                    allUserPostsList.add(bitmap)
                    if (i == amountOfWorks) allUserPosts.postValue(allUserPostsList)
                }
            }

        }.addOnFailureListener { exception ->
            dataChangeExceptions.postValue(exception.toString())
            getData()
        }

    }

    fun saveImage(filePath : Uri) {
        amountOfWorks += 1
        imageReference = storageReference.child("uploads/$uid/$amountOfWorks")

        imageReference.putFile(filePath).addOnSuccessListener {
            currentUserDb.child("userData").child("worksAmount").setValue(amountOfWorks)
        }
    }

    fun saveImageDescription(description: String) {
        currentUserDb.child("profileData").child("posts")
            .child("$amountOfWorks").child("description").setValue(description)
    }

    fun saveHashtags(byHandSelected: Boolean,
                     depressedButtonSelected: Boolean, funButtonSelected: Boolean,
                     stillLifeButtonSelected: Boolean, portraitButtonSelected: Boolean,
                     landscapeButtonSelected: Boolean, marineButtonSelected: Boolean,
                     battlePaintingButtonSelected: Boolean, interiorButtonSelected: Boolean,
                     caricatureButtonSelected: Boolean, nudeButtonSelected: Boolean,
                     animeButtonSelected: Boolean, horrorButtonSelected: Boolean) {


        when {
            depressedButtonSelected -> currentUserDb.child("profileData").child("posts")
                .child("$amountOfWorks").child("hashtags")
                .child("mood").setValue("depressed")
            funButtonSelected -> currentUserDb.child("profileData").child("posts")
                .child("$amountOfWorks").child("hashtags")
                .child("mood").setValue("fun")
            else -> currentUserDb.child("profileData").child("posts")
                .child("$amountOfWorks").child("hashtags")
                .child("mood").setValue("ignore")
        }


        if (byHandSelected)
            currentUserDb.child("profileData").child("posts")
                .child("$amountOfWorks").child("hashtags")
                .child("technique").setValue("byHand")
        else currentUserDb.child("profileData").child("posts")
            .child("$amountOfWorks").child("hashtags")
            .child("technique").setValue("computerGraphics")


        if (stillLifeButtonSelected) currentUserDb.child("profileData").child("posts")
            .child("$amountOfWorks").child("hashtags")
            .child("genre").setValue("stillLife")
        if (portraitButtonSelected) currentUserDb.child("profileData").child("posts")
            .child("$amountOfWorks").child("hashtags")
            .child("genre").setValue("portrait")
        if (landscapeButtonSelected) currentUserDb.child("profileData").child("posts")
            .child("$amountOfWorks").child("hashtags")
            .child("genre").setValue("landscape")
        if (marineButtonSelected) currentUserDb.child("profileData").child("posts")
            .child("$amountOfWorks").child("hashtags")
            .child("genre").setValue("marine")
        if (battlePaintingButtonSelected) currentUserDb.child("profileData").child("posts")
            .child("$amountOfWorks").child("hashtags")
            .child("genre").setValue("battlePainting")
        if (interiorButtonSelected) currentUserDb.child("profileData").child("posts")
            .child("$amountOfWorks").child("hashtags")
            .child("genre").setValue("interior")
        if (caricatureButtonSelected) currentUserDb.child("profileData").child("posts")
            .child("$amountOfWorks").child("hashtags")
            .child("genre").setValue("caricature")
        if (nudeButtonSelected) currentUserDb.child("profileData").child("posts")
            .child("$amountOfWorks").child("hashtags")
            .child("genre").setValue("nude")
        if (animeButtonSelected) currentUserDb.child("profileData").child("posts")
            .child("$amountOfWorks").child("hashtags")
            .child("genre").setValue("anime")
        if (horrorButtonSelected) currentUserDb.child("profileData").child("posts")
            .child("$amountOfWorks").child("hashtags")
            .child("genre").setValue("horror")
    }
}
