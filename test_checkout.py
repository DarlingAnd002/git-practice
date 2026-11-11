import unittest
from checkout import apply_student_discount


class TestStudentDiscount(unittest.TestCase):
    def test_student(self):
        self.assertAlmostEqual(apply_student_discount(100, True), 90)

    def test_non_student(self):
        self.assertAlmostEqual(apply_student_discount(100, False), 100)

    def test_another_total(self):
        self.assertAlmostEqual(apply_student_discount(250, True), 225)

    def test_zero(self):
        self.assertEqual(apply_student_discount(0, True), 0)


if __name__ == "__main__":
    unittest.main()