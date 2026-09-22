package com.system.update.games

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
    var tiles by remember { mutableStateOf<List<Int>>(listOf()) }
    var moves by remember { mutableStateOf(0) }
    var solved by remember { mutableStateOf(false) }

    fun shuffle() {
        val list = (1..15).toMutableList()
        list.add(0)
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
        tiles = list.toList()
        moves = 0
        solved = false
    }

    LaunchedEffect(Unit) { shuffle() }

    LaunchedEffect(tiles) {
        if (tiles.size == 16) {
            var ok = true
            for (i in 0 until 15) {
                if (tiles[i] != i + 1) { ok = false; break }
            }
            if (ok && tiles[15] == 0) solved = true
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
                Text("Kembali", color = Color(0xFFD4AF37))
            }
            Spacer(Modifier.weight(1f))
            Text("Langkah: $moves", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        if (solved) {
            Text("SELESAI! $moves langkah", color = Color(0xFF00FF66), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
        }

        if (tiles.size == 16) {
            for (r in 0..3) {
                Row {
                    for (c in 0..3) {
                        val idx = r * 4 + c
                        val v = tiles[idx]
                        val isEmpty = v == 0
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isEmpty) Color(0xFF0A0A0A) else Color(0xFF1C1C1C))
                                .clickable(enabled = !isEmpty) {
                                    val empty = tiles.indexOf(0)
                                    val er = empty / 4
                                    val ec = empty % 4
                                    if (abs(r - er) + abs(c - ec) == 1) {
                                        val newList = tiles.toMutableList()
                                        newList[empty] = v
                                        newList[idx] = 0
                                        tiles = newList.toList()
                                        moves++
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!isEmpty) {
                                Text(
                                    "$v",
                                    color = Color(0xFFD4AF37),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
