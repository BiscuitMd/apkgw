package com.system.update.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import kotlin.math.abs

private typealias Board = Array<Array<String>>

@Composable
fun ChessGame(onBack: () -> Unit) {
    var board by remember { mutableStateOf(initialBoard()) }
    var selected by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var whiteTurn by remember { mutableStateOf(true) }
    var legalMoves by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var message by remember { mutableStateOf("") }

    fun reset() {
        board = initialBoard()
        selected = null
        legalMoves = emptyList()
        whiteTurn = true
        message = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .background(Color(0xFF0A0A0A)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("← Kembali", color = Color(0xFFD4AF37))
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (whiteTurn) "Giliran Putih" else "Giliran Hitam",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .border(2.dp, Color(0xFFD4AF37), RoundedCornerShape(6.dp))
                .padding(2.dp)
        ) {
            Column {
                for (r in 0..7) {
                    Row {
                        for (c in 0..7) {
                            val piece = board[r][c]
                            val isDark = (r + c) % 2 == 0
                            val isSel = selected == (r to c)
                            val isLegal = legalMoves.contains(r to c)

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        when {
                                            isSel -> Color(0xFFD4AF37)
                                            isLegal -> Color(0xFF4A7A4A)
                                            isDark -> Color(0xFF2A2A2A)
                                            else -> Color(0xFFEDEDED)
                                        }
                                    )
                                    .clickable {
                                        when {
                                            // Belum ada yang dipilih
                                            selected == null -> {
                                                if (piece != "" && isWhitePiece(piece) == whiteTurn) {
                                                    selected = r to c
                                                    legalMoves = getLegalMoves(board, r, c)
                                                }
                                            }
                                            // Sudah ada yang dipilih, klik di posisi yang sama = batal
                                            selected == (r to c) -> {
                                                selected = null
                                                legalMoves = emptyList()
                                            }
                                            // Sudah ada yang dipilih, klik posisi lain
                                            else -> {
                                                if (legalMoves.contains(r to c)) {
                                                    val (sr, sc) = selected!!
                                                    val moving = board[sr][sc]
                                                    val captured = board[r][c]
                                                    val nb = board.map { it.copyOf() }.toTypedArray()
                                                    nb[r][c] = moving
                                                    nb[sr][sc] = ""
                                                    board = nb
                                                    selected = null
                                                    legalMoves = emptyList()
                                                    whiteTurn = !whiteTurn
                                                    message = if (captured != "") "Dimakan!" else ""
                                                } else {
                                                    // Coba pilih bidak lain
                                                    if (piece != "" && isWhitePiece(piece) == whiteTurn) {
                                                        selected = r to c
                                                        legalMoves = getLegalMoves(board, r, c)
                                                    } else {
                                                        selected = null
                                                        legalMoves = emptyList()
                                                    }
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (piece != "") {
                                    Text(piece, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            message,
            color = Color(0xFFD4AF37),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { reset() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A0000))
        ) {
            Text("Reset Papan", color = Color.White)
        }
    }
}

// ============================================================
// BOARD SETUP
// ============================================================
private fun initialBoard(): Board = arrayOf(
    arrayOf("♜","♞","♝","♛","♚","♝","♞","♜"),
    arrayOf("♟","♟","♟","♟","♟","♟","♟","♟"),
    Array(8) { "" }, Array(8) { "" }, Array(8) { "" }, Array(8) { "" },
    arrayOf("♙","♙","♙","♙","♙","♙","♙","♙"),
    arrayOf("♖","♘","♗","♕","♔","♗","♘","♖")
)

private fun isWhitePiece(p: String): Boolean = p in listOf("♔","♕","♖","♗","♘","♙")

private fun getLegalMoves(board: Board, r: Int, c: Int): List<Pair<Int, Int>> {
    val piece = board[r][c]
    if (piece == "") return emptyList()
    val white = isWhitePiece(piece)
    val moves = mutableListOf<Pair<Int, Int>>()

    fun addIfValid(nr: Int, nc: Int) {
        if (nr in 0..7 && nc in 0..7) {
            val target = board[nr][nc]
            if (target == "" || isWhitePiece(target) != white) {
                moves.add(nr to nc)
            }
        }
    }

    fun slide(dr: Int, dc: Int) {
        var nr = r + dr
        var nc = c + dc
        while (nr in 0..7 && nc in 0..7) {
            val target = board[nr][nc]
            if (target == "") {
                moves.add(nr to nc)
            } else {
                if (isWhitePiece(target) != white) moves.add(nr to nc)
                break
            }
            nr += dr
            nc += dc
        }
    }

    when (piece) {
        "♙" -> { // Pawn putih
            if (r - 1 >= 0 && board[r-1][c] == "") {
                moves.add((r-1) to c)
                if (r == 6 && board[r-2][c] == "") moves.add((r-2) to c)
            }
            if (r - 1 >= 0 && c - 1 >= 0 && board[r-1][c-1] != "" && !isWhitePiece(board[r-1][c-1])) moves.add((r-1) to (c-1))
            if (r - 1 >= 0 && c + 1 <= 7 && board[r-1][c+1] != "" && !isWhitePiece(board[r-1][c+1])) moves.add((r-1) to (c+1))
        }
        "♟" -> { // Pawn hitam
            if (r + 1 <= 7 && board[r+1][c] == "") {
                moves.add((r+1) to c)
                if (r == 1 && board[r+2][c] == "") moves.add((r+2) to c)
            }
            if (r + 1 <= 7 && c - 1 >= 0 && board[r+1][c-1] != "" && isWhitePiece(board[r+1][c-1])) moves.add((r+1) to (c-1))
            if (r + 1 <= 7 && c + 1 <= 7 && board[r+1][c+1] != "" && isWhitePiece(board[r+1][c+1])) moves.add((r+1) to (c+1))
        }
        "♖", "♜" -> { slide(-1,0); slide(1,0); slide(0,-1); slide(0,1) }
        "♗", "♝" -> { slide(-1,-1); slide(-1,1); slide(1,-1); slide(1,1) }
        "♕", "♛" -> { slide(-1,0); slide(1,0); slide(0,-1); slide(0,1); slide(-1,-1); slide(-1,1); slide(1,-1); slide(1,1) }
        "♘", "♞" -> {
            val offsets = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
            offsets.forEach { (dr, dc) -> addIfValid(r + dr, c + dc) }
        }
        "♔", "♚" -> {
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                addIfValid(r + dr, c + dc)
            }
        }
    }
    return moves
}
