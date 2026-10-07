package edu.vitap.common;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity @Table(name="app_users", catalog="courier_users")
public class AppUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true) private String email;
    @Column(nullable=false) @JsonIgnore private String password;
    @Column(nullable=false) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private Role role;
    protected AppUser() {}
    public AppUser(String email,String password,String name,Role role){this.email=email;this.password=password;this.name=name;this.role=role;}
    public Long getId(){return id;} public String getEmail(){return email;} public String getPassword(){return password;} public String getName(){return name;} public Role getRole(){return role;}
}
