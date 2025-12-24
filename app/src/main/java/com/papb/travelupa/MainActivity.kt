package com.papb.travelupa

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.papb.travelupa.ui.theme.TravelupaTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseUser

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        val currentUser = FirebaseAuth.getInstance().currentUser

        setContent {
            TravelupaTheme {
                AppNavigation(currentUser = currentUser)
            }
        }
    }
}

// --- NAVIGASI ---
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
}

@Composable
fun AppNavigation(currentUser: FirebaseUser?) { // Menerima parameter user
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()

    // BAB 6: Tentukan startDestination berdasarkan status login
    val startDest = if (currentUser != null) Screen.Home.route else Screen.Login.route

    NavHost(navController = navController, startDestination = startDest) {

        // Screen Login
        composable(Screen.Login.route) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            })
        }

        // Screen Home (Rekomendasi Tempat)
        composable(Screen.Home.route) {
            // Kita gunakan CoroutineScope di dalam screen ini untuk Logout nanti
            val scope = rememberCoroutineScope()

            RekomendasiTempatScreen(onLogout = {
                scope.launch {
                    auth.signOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            })
        }
    }
}

// --- HALAMAN LOGIN ---
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Login Travelupa", style = MaterialTheme.typography.h4)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage != null) {
            Text(text = errorMessage!!, color = Color.Red)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = "Email dan Password tidak boleh kosong"
                    return@Button
                }
                isLoading = true
                scope.launch {
                    try {
                        auth.signInWithEmailAndPassword(email, password).await()
                        isLoading = false
                        onLoginSuccess()
                    } catch (e: Exception) {
                        try {
                            auth.createUserWithEmailAndPassword(email, password).await()
                            isLoading = false
                            onLoginSuccess()
                        } catch (regError: Exception) {
                            isLoading = false
                            errorMessage = "Error: ${e.message}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("Masuk / Daftar")
        }
    }
}

// --- HALAMAN UTAMA (FIRESTORE) ---
data class TempatWisata(
    val id: String = "",
    val nama: String = "",
    val deskripsi: String = "",
    val gambarUriString: String? = null
)

@Composable
fun RekomendasiTempatScreen(onLogout: () -> Unit) {
    val firestore = FirebaseFirestore.getInstance()
    var daftarTempatWisata by remember { mutableStateOf(listOf<TempatWisata>()) }
    var showTambahDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Baca data dari Firestore saat pertama kali dibuka
    LaunchedEffect(Unit) {
        firestore.collection("tempat_wisata").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val list = snapshot.documents.map { doc ->
                    TempatWisata(
                        id = doc.id,
                        nama = doc.getString("nama") ?: "",
                        deskripsi = doc.getString("deskripsi") ?: "",
                        gambarUriString = doc.getString("gambarUriString")
                    )
                }
                daftarTempatWisata = list
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Travelupa") },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showTambahDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Tambah")
            }
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            items(daftarTempatWisata) { tempat ->
                TempatItemFirestore(tempat) {
                    // Hapus data dari Firestore
                    firestore.collection("tempat_wisata").document(tempat.id).delete()
                }
            }
        }

        if (showTambahDialog) {
            TambahTempatDialogFirestore(
                onDismiss = { showTambahDialog = false },
                onTambah = { nama, deskripsi, uri ->
                    val data = hashMapOf(
                        "nama" to nama,
                        "deskripsi" to deskripsi,
                        "gambarUriString" to (uri?.toString() ?: "")
                    )
                    firestore.collection("tempat_wisata").add(data)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Berhasil disimpan!", Toast.LENGTH_SHORT).show()
                            showTambahDialog = false
                        }
                }
            )
        }
    }
}

@Composable
fun TempatItemFirestore(tempat: TempatWisata, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), elevation = 4.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!tempat.gambarUriString.isNullOrEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(tempat.gambarUriString),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).clickable{}, contentAlignment = Alignment.Center) {
                    Text("No Image")
                }
            }
            Text(tempat.nama, style = MaterialTheme.typography.h6)
            Text(tempat.deskripsi, style = MaterialTheme.typography.body2)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = Color.Red)
            }
        }
    }
}

@Composable
fun TambahTempatDialogFirestore(onDismiss: () -> Unit, onTambah: (String, String, Uri?) -> Unit) {
    var nama by remember { mutableStateOf("") }
    var deskripsi by remember { mutableStateOf("") }
    var gambarUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { gambarUri = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Data (Cloud)") },
        text = {
            Column {
                OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama") })
                OutlinedTextField(value = deskripsi, onValueChange = { deskripsi = it }, label = { Text("Deskripsi") })
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { launcher.launch("image/*") }) {
                    Text(if (gambarUri != null) "Gambar Terpilih" else "Pilih Gambar")
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (nama.isNotBlank()) onTambah(nama, deskripsi, gambarUri) }) {
                Text("Simpan")
            }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Batal") } }
    )
}