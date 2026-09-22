package com.system.update.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun BottleShotGame(onBack: () -> Unit) {
    var bottles by remember { mutableStateOf(List(8) { true }) }
    var score by remember { mutableStateOf(0) }
    var highScore by remember { mutableStateOf(0) }
    var ammo by remember { mutableStateOf(10) }
    var message by remember { mutableStateOf("") }
    var lastHit by remember { mutableStateOf(-1) }
    var timeLeft by remember { mutableStateOf(30) }
    var gameActive by remember { mutableStateOf(false) }

    // Timer
    LaunchedEffect(gameActive) {
        if (gameActive) {
            while (timeLeft > 0 && gameActive) {
                delay(1000)
                timeLeft--
            }
            if (timeLeft == 0) {
                gameActive = false
                if (score > highScore) highScore = score
                message = "Waktu habis!"
            }
        }
    }

    fun startGame() {
        bottles = List(8) { true }
        score = 0
        ammo = 10
        message = ""
        lastHit = -1
        timeLeft = 30
        gameActive = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A0A), Color(0xFF1A0A00))
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("← Kembali", color = Color(0xFFD4AF37))
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("Skor: $score", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Terbaik: $highScore", color = Color(0xFF9A9A9A), fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // HUD
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            HudCard("⏱", "$timeLeft s", Color(0xFF00FF66))
            HudCard("🎯", "$ammo", Color(0xFFD4AF37))
            HudCard("🍾", bottles.count { it }.toString(), Color(0xFFE60000))
        }

        Spacer(Modifier.height(32.dp))

        // Bottle row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bottles.forEachIndexed { i, alive ->
                val isHit = lastHit == i
                val scale by animateFloatAsState(
                    targetValue = if (isHit) 1.2f else 1f,
                    label = "bottle_scale"
                )
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 100.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(
                            if (alive)
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF2E8B57), Color(0xFF1C6B3A))
                                )
                            else
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF1C1C1C), Color(0xFF0A0A0A))
                                )
                        )
                        .clickable(enabled = gameActive && alive && ammo > 0) {
                            ammo--
                            score += 10
                            bottles = bottles.toMutableList().also { it[i] = false }
                            lastHit = i
                            message = "KENA! +10"
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (alive) {
                        Text("🍾", fontSize = 24.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            message.ifEmpty { if (!gameActive && timeLeft == 30) "Tekan Mulai untuk bermain" else "" },
            color = Color(0xFFD4AF37),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { startGame() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A0000)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (gameActive) "RESTART" else "MULAI",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun HudCard(icon: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
