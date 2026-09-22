package com.system.update.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

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
fun HomeScreen(
    onChess: () -> Unit,
    onPuzzle: () -> Unit,
    onBottle: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A0A), Color(0xFF1A0000))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "GAME HUB",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD4AF37),
                        letterSpacing = 4.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pilih permainan",
                        color = Color(0xFF9A9A9A),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically()
            ) {
                Column {
                    GameCard(
                        icon = "♟",
                        title = "Catur",
                        subtitle = "Main catur klasik",
                        onClick = onChess
                    )
                    Spacer(Modifier.height(14.dp))
                    GameCard(
                        icon = "🧩",
                        title = "Puzzle",
                        subtitle = "Susun angka 1-15",
                        onClick = onPuzzle
                    )
                    Spacer(Modifier.height(14.dp))
                    GameCard(
                        icon = "🍾",
                        title = "Tembak Botol",
                        subtitle = "Latih akurasi",
                        onClick = onBottle
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "SYSTEM UPDATE v1.0",
                color = Color(0xFF3A3A3A),
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
fun GameCard(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF8A0000), Color(0xFFD4AF37))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 26.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    fontSize = 18.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF9A9A9A)
                )
            }
        }
    }
}
