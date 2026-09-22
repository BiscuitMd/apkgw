package com.system.update.games

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun PuzzleGame(onBack: () -> Unit) {
    var tiles by remember { mutableStateOf((1..15).toMutableList() + 0) }
    var moves by remember { mutableStateOf(0) }
    var solved by remember { mutableStateOf(false) }

    fun shuffle() {
        do {
            val list = (1..15).toMutableList() + 0
            repeat(300) {
                val empty = list.indexOf(0)
                val r = empty / 4
                val c = empty % 4
                val opts = mutableListOf<Int>()
                if (r > 0) opts.add(empty - 4)
                if (r < 3) opts.add(empty + 4)
                if (c > 0) opts.add(empty - 1)
                if (c < 3) opts.add(empty + 1)
                val pick = opts.random()
                val tmp = list[empty]
                list[empty] = list[pick]
                list[pick] = tmp
            }
            tiles = list
        } while (isSolved(tiles))
        moves = 0
        solved = false
    }

    LaunchedEffect(Unit) { shuffle() }

    // Check solved
    LaunchedEffect(tiles) {
        if (isSolved(tiles)) {
            solved = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color(0xFF0A0A0A)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("← Kembali", color = Color(0xFFD4AF37))
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Langkah: $moves",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        if (solved) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C6B3A))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎉 SELESAI!", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("$moves langkah", color = Color.White, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141414))
                .padding(8.dp)
        ) {
            Column {
                for (r in 0..3) {
                    Row {
                        for (c in 0..3) {
                            val idx = r * 4 + c
                            val v = tiles[idx]
                            val isEmpty = v == 0
                            val bgColor by animateColorAsState(
                                targetValue = if (isEmpty) Color(0xFF0A0A0A)
                                              else tileColor(v),
                                label = "tileColor"
                            )
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgColor)
                                    .clickable(enabled = !isEmpty) {
                                        val empty = tiles.indexOf(0)
                                        val er = empty / 4
                                        val ec = empty % 4
                                        if (abs(r - er) + abs(c - ec) == 1) {
                                            val newList = tiles.toMutableList()
                                            newList[empty] = v
                                            newList[idx] = 0
                                            tiles = newList
                                            moves++
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!isEmpty) {
                                    Text(
                                        "$v",
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { shuffle() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A0000))
        ) {
            Text("Acak Ulang", color = Color.White)
        }
    }
}

private fun isSolved(tiles: List<Int>): Boolean {
    for (i in 0 until 15) {
        if (tiles[i] != i + 1) return false
    }
    return tiles[15] == 0
}

private fun tileColor(v: Int): Color {
    // Warna bervariasi per tile biar puzzle terlihat seperti gambar terbagi
    val palette = listOf(
        Color(0xFF8A0000), Color(0xFFB30000), Color(0xFFD4AF37),
        Color(0xFF6B4F0F), Color(0xFF1C6B3A), Color(0xFF0F4F8A),
        Color(0xFF8A1C6B), Color(0xFF4F3A0F), Color(0xFFB36A00),
        Color(0xFF2A2A2A), Color(0xFF3A2A1C), Color(0xFF1C3A4F),
        Color(0xFF4F1C3A), Color(0xFF2A4F2A), Color(0xFF4F2A1C)
    )
    return palette[(v - 1) % palette.size]
}
