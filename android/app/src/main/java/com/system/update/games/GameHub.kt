package com.system.update.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GameHub() {
    var screen by remember { mutableStateOf("home") }
    when (screen) {
        "home" -> HomeScreen(
            onChess = { screen = "chess" },
            onPuzzle = { screen = "puzzle" },
            onBottle = { screen = "bottle" }
        )
        "chess" -> ChessGame(onBack = { screen = "home" })
        "puzzle" -> PuzzleGame(onBack = { screen = "home" })
        "bottle" -> BottleShotGame(onBack = { screen = "home" })
    }
}

@Composable
fun HomeScreen(onChess: () -> Unit, onPuzzle: () -> Unit, onBottle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(Color(0xFF0A0A0A)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("GAME HUB", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD4AF37))
        Spacer(Modifier.height(4.dp))
        Text("Pilih permainan", color = Color(0xFF9A9A9A), fontSize = 13.sp)
        Spacer(Modifier.height(32.dp))
        GameCard("Catur", onChess)
        Spacer(Modifier.height(12.dp))
        GameCard("Puzzle", onPuzzle)
        Spacer(Modifier.height(12.dp))
        GameCard("Tembak Botol", onBottle)
    }
}

@Composable
fun GameCard(title: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(70.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
