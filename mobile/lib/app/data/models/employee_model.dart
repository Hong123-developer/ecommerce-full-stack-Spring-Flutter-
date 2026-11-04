/// Model representing an employee for CRUD operations.
///
/// Used with mock data since the backend does not have employee endpoints.
class EmployeeModel {
  final int? id;
  final String name;
  final String position;
  final String department;
  final double salary;
  final String? phone;
  final String? email;
  final String? imageUrl;
  final String? createdAt;

  EmployeeModel({
    this.id,
    required this.name,
    required this.position,
    required this.department,
    required this.salary,
    this.phone,
    this.email,
    this.imageUrl,
    this.createdAt,
  });

  /// Creates an [EmployeeModel] from a JSON map.
  factory EmployeeModel.fromJson(Map<String, dynamic> json) {
    return EmployeeModel(
      id: json['id'],
      name: json['name'] ?? '',
      position: json['position'] ?? '',
      department: json['department'] ?? '',
      salary: (json['salary'] ?? 0).toDouble(),
      phone: json['phone'],
      email: json['email'],
      imageUrl: json['imageUrl'],
      createdAt: json['createdAt'],
    );
  }

  /// Serializes this [EmployeeModel] to a JSON map.
  Map<String, dynamic> toJson() {
    return {
      if (id != null) 'id': id,
      'name': name,
      'position': position,
      'department': department,
      'salary': salary,
      'phone': phone,
      'email': email,
      'imageUrl': imageUrl,
    };
  }

  /// Creates a copy of this [EmployeeModel] with the given fields replaced.
  EmployeeModel copyWith({
    int? id,
    String? name,
    String? position,
    String? department,
    double? salary,
    String? phone,
    String? email,
    String? imageUrl,
    String? createdAt,
  }) {
    return EmployeeModel(
      id: id ?? this.id,
      name: name ?? this.name,
      position: position ?? this.position,
      department: department ?? this.department,
      salary: salary ?? this.salary,
      phone: phone ?? this.phone,
      email: email ?? this.email,
      imageUrl: imageUrl ?? this.imageUrl,
      createdAt: createdAt ?? this.createdAt,
    );
  }
}
