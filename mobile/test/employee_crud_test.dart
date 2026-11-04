import 'package:flutter_test/flutter_test.dart';
import 'package:get/get.dart';
import 'package:ecomerce_shop/app/data/models/employee_model.dart';
import 'package:ecomerce_shop/app/data/providers/employee_provider.dart';
import 'package:ecomerce_shop/app/data/repositories/employee_repository.dart';
import 'package:ecomerce_shop/app/controllers/employee_controller.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Employee Management Unit Tests', () {
    late EmployeeProvider provider;
    late EmployeeRepository repository;
    late EmployeeController controller;

    setUp(() {
      Get.reset();
      provider = EmployeeProvider();
      repository = EmployeeRepository(provider);
      controller = EmployeeController(repository);
    });

    test('1. EmployeeModel Serialization Test', () {
      final json = {
        'id': 10,
        'name': 'សាកល្បង',
        'position': 'Developer',
        'department': 'IT',
        'salary': 1500.0,
        'phone': '012345678',
        'email': 'test@test.com',
      };

      final employee = EmployeeModel.fromJson(json);
      expect(employee.id, 10);
      expect(employee.name, 'សាកល្បង');
      expect(employee.salary, 1500.0);

      final outJson = employee.toJson();
      expect(outJson['name'], 'សាកល្បង');
      expect(outJson['salary'], 1500.0);
    });

    test('2. EmployeeProvider & Repository Get All Employees Test', () async {
      final list = await repository.getEmployees();
      expect(list.isNotEmpty, true);
      expect(list.length >= 5, true);
    });

    test('3. EmployeeRepository Create, Update, Delete Test', () async {
      // Create
      final newEmp = EmployeeModel(
        name: 'បុគ្គលិកថ្មី',
        position: 'QA Tester',
        department: 'IT',
        salary: 800.0,
      );
      final created = await repository.createEmployee(newEmp);
      expect(created.id, isNotNull);
      expect(created.name, 'បុគ្គលិកថ្មី');

      // Update
      final updated = await repository.updateEmployee(
        created.id!,
        created.copyWith(position: 'Senior QA Tester', salary: 1100.0),
      );
      expect(updated.position, 'Senior QA Tester');
      expect(updated.salary, 1100.0);

      // Delete
      final deleted = await repository.deleteEmployee(created.id!);
      expect(deleted, true);
    });

    test('4. Form Validation Tests in EmployeeController', () {
      // Required Field
      expect(controller.validateRequired('', 'ឈ្មោះ'), isNotNull);
      expect(controller.validateRequired('សុខា', 'ឈ្មោះ'), isNull);

      // Salary Validation
      expect(controller.validateSalary(''), isNotNull);
      expect(controller.validateSalary('-10'), isNotNull);
      expect(controller.validateSalary('0'), isNotNull);
      expect(controller.validateSalary('1000'), isNull);

      // Email Validation
      expect(controller.validateEmail('invalid-email'), isNotNull);
      expect(controller.validateEmail('test@company.com'), isNull);
    });
  });
}

