    import com.google.protobuf.ByteString;
    import io.grpc.Server;
    import io.grpc.ServerBuilder;
    import io.grpc.stub.StreamObserver;

    import java.io.*;

    public class GrpcServer {

        private static final String SERVER_IMAGE_DIRECTORY = "D:\\Uni\\_PWR_SEM6\\RSI\\lab2\\Obrazki\\Server\\";

        public static void main(String[] args) {
            int port = 50001;


            System.out.println("Starting gRPC server...");
            Server server = ServerBuilder.forPort(port)
                    .addService(new MyServiceImpl())
                    .build();
            try {
                server.start();
                System.out.println("...Server started");
                server.awaitTermination();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        private static String saveImageToServer(byte[] imageData, String imageName, Database db, int imageId) {
            String new_imageName = "Obrazek.jpg";
            for (User user: db.getAllUsers()){
                if (user.getImageId() == imageId){
                    new_imageName = imageId + "_Obrazek.jpg";
                    break;
                }
            }

            String imagePath = SERVER_IMAGE_DIRECTORY + new_imageName;
            System.out.println("\nSaving image to: " + imagePath);
            try {
                File file = new File(imagePath);
                FileOutputStream outputStream = new FileOutputStream(file);
                outputStream.write(imageData);
                outputStream.close();
                return imageName;
            } catch (IOException e) {
                e.printStackTrace();
                return null; // Or handle error as needed
            }
        }

        static class MyServiceImpl extends ServiceNameGrpc.ServiceNameImplBase {

            Database db = new Database();

            public void writeRecord(WriteRecordRequest req, StreamObserver<WriteRecordResponse> responseObserver){
                System.out.println("...called writeRecord");
                db.addUser(req.getName(), req.getAge(), req.getImageId());
                WriteRecordResponse response = WriteRecordResponse.newBuilder()
                        .setSuccess(true)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }

            public void readRecord(ReadRecordRequest req, StreamObserver<ReadRecordResponse> responseObserver){
                System.out.println("...called readRecord");
                User user = db.findUserById(req.getRecordId());
                ReadRecordResponse response;
                if (user != null) {
                    response = ReadRecordResponse.newBuilder()
                            .setRecordData("ID: " + user.getId() + ", Name: " + user.getName() + ", Age: " + user.getAge() + " Image id " + user.getImageId() + "\n")
                            .build();
                } else {
                    response = ReadRecordResponse.newBuilder()
                            .setRecordData("Record not found\n")
                            .build();
                }
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }

            public void searchRecords(SearchRecordsRequest req, StreamObserver<SearchRecordsResponse> responseObserver){
                System.out.println("...called searchRecords");
                StringBuilder msg = new StringBuilder();
                for (User user : db.getAllUsers()) {
                    if (user.getAge() == req.getAge()) {
                        msg.append("ID: ").append(user.getId()).append(", Name: ").append(user.getName()).append(", Age: ").append(user.getAge()).append("\n");
                    }
                }
                SearchRecordsResponse response = SearchRecordsResponse.newBuilder()
                        .setMessage(msg.toString())
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }

            public void displayAllRecords(DisplayAllRecordsRequest req, StreamObserver<DisplayAllRecordsResponse> responseObserver){
                StringBuilder msg = new StringBuilder();
                System.out.println("...called displayAllUsers");
                for (User user : db.getAllUsers()) {
                    msg.append("ID: ").append(user.getId()).append(", Name: ").append(user.getName()).append(", Age: ").append(user.getAge()).append(", Image ID: ").append(user.getImageId()).append("\n");
                }

                DisplayAllRecordsResponse response = DisplayAllRecordsResponse.newBuilder()
                        .setRecordData(msg.toString())
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }

            public void displayAllImages(DisplayAllImagesRequest req, StreamObserver<DisplayAllImagesResponse> responseObserver){
                StringBuilder msg = new StringBuilder();
                System.out.println("...called displayAllImages");
                for (Image image : db.getAllImages()) {
                    msg.append("ID: ").append(image.getId()).append(", Name: ").append(image.getPath()).append("\n");
                }

                DisplayAllImagesResponse response = DisplayAllImagesResponse.newBuilder()
                        .setImagesData(msg.toString())
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }

            public void downloadImage (DownloadImageRequest request,
                                       StreamObserver<DownloadImageResponse> responseObserver) {
                String imageName = request.getImageName();
                String imagePath = SERVER_IMAGE_DIRECTORY + imageName;
                System.out.println("Downloading image: " + imageName);

                try (FileInputStream inputStream = new FileInputStream(imagePath)) {
                    byte[] buffer = new byte[128];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        DownloadImageResponse response = DownloadImageResponse.newBuilder()
                                .setChunk(ByteString.copyFrom(buffer, 0, bytesRead))
                                .build();
                        responseObserver.onNext(response);
                        Thread.sleep(50);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    responseObserver.onError(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                responseObserver.onCompleted();
            }

            @Override
            public StreamObserver<UploadImageRequest> uploadImage(StreamObserver<UploadImageResponse> responseObs) {
                return new StreamObserver<UploadImageRequest>() {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    String imageName;

                    @Override
                    public void onNext(UploadImageRequest request) {
                        try {
                            if (imageName == null) {
                                imageName = request.getImageName();
                            }
                            outputStream.write(request.getChunk().toByteArray());

                            int chunkSize = request.getChunk().size();

                            UploadImageResponse response = UploadImageResponse.newBuilder()
                                    .setSuccess(false)
                                    .setNumOfBytes("")
                                    .build();
                            responseObs.onNext(response);
                            Thread.sleep(5);
                        } catch (IOException e) {
                            onError(e);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        // Handle error
                    }

                    @Override
                    public void onCompleted() {
                        int imageId = db.addImage(imageName);
                        // Save the received image to the server's directory
                        //saveImageToServer(outputStream.toByteArray(), imageName);
                        saveImageToServer(outputStream.toByteArray(), imageName, db, imageId);
                        UploadImageResponse response = UploadImageResponse.newBuilder()
                                .setSuccess(true)
                                .setNumOfBytes("*".repeat(outputStream.size()))
                                .build();
                        responseObs.onNext(response);
                        responseObs.onCompleted();
                    }
                };
            }
        }
    }

