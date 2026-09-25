package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testSenderFilteringLogic() {
    val allowedSenders = setOf("bKash", "Nagad", "16216", "16167", "ROCKET", "UPAY")

    fun isAllowed(sender: String): Boolean {
      val normalized = sender.trim().uppercase()
      return allowedSenders.any { it.trim().uppercase() == normalized }
    }

    assertTrue(isAllowed("bKash"))
    assertTrue(isAllowed("BKASH"))
    assertTrue(isAllowed("bkash"))
    assertTrue(isAllowed("16216"))
    assertTrue(isAllowed("Nagad"))
    assertFalse(isAllowed("SpamPromo"))
    assertFalse(isAllowed("RandomSender"))
  }
}


