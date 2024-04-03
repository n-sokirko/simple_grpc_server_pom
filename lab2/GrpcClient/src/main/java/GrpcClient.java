import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Scanner;

public class GrpcClient {

    private static final String CLIENT_IMAGE_DIRECTORY = "D:\\Uni\\_PWR_SEM6\\RSI\\lab2\\Obrazki\\Client\\";

    private static class SearchObs implements StreamObserver<SearchRecordsResponse> {
        public void onNext(SearchRecordsResponse theResponse) {
            System.out.println(theResponse.getMessage());
        }
        public void onError(Throwable throwable) {
            System.out.println("Podczas wyszukiwania osób wystąpił błąd");
        }
        public void onCompleted() {
            System.out.println("Zakończono wyszukiwanie osób");
        }
    }

    private static class StreamUploadImageObs implements StreamObserver<UploadImageResponse>{
        int counter = 0;

        public void onNext(UploadImageResponse theResponse) {
            if (!theResponse.getSuccess()) {
                if (counter % 10 == 0) {
                    System.out.print("*");
                }
                counter++;
            }
        }
        public void onError(Throwable throwable) {
            System.out.println("Podczas przesyłania obrazka wystąpił błąd");
        }
        public void onCompleted() {
            System.out.println("\nZakończono przesyłanie obrazka");
        }
    }

    private static class StreamDownloadImageObs implements StreamObserver<DownloadImageResponse>{
        String imageName;
        FileOutputStream outputStream;
        public StreamDownloadImageObs(String imageName) {
            this.imageName = imageName;
            try {
                outputStream = new FileOutputStream(CLIENT_IMAGE_DIRECTORY + imageName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void onNext(DownloadImageResponse response) {
            try {
                outputStream.write(response.getChunk().toByteArray());
                System.out.print("*");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            throwable.printStackTrace();
        }

        @Override
        public void onCompleted() {
            try {
                outputStream.close();
                System.out.println("\nZakończono przesyłanie obrazka");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        String address = "localhost"; // Service is on the same host
        int port = 50001;
        Scanner scanner = new Scanner(System.in);
        ManagedChannel channel;
        ServiceNameGrpc.ServiceNameStub nonbStub;
        ServiceNameGrpc.ServiceNameBlockingStub bStub;

        System.out.println("Running gRPC client...");
        while (true) {
            System.out.println("Wybierz opcję:" +
                    "\n1. Zapisać osobę"
                    + "\n2. Odczytać osobę"
                    + "\n3. Odczytać wszystkie osoby"
                    + "\n4. Odczytać osób o podanym wieku"
                    + "\n5. Wgrać obrazek"
                    + "\n6. Odczytać wszystkie obrazki"
                    + "\n7. Pobrać obrazek"
                    + "\n8. Exit");

            int choice = scanner.nextInt();
            switch (choice) {
                case 1:
                    System.out.println("Podaj imię: ");
                    String name = scanner.next();
                    System.out.println("Podaj wiek: ");
                    int age = scanner.nextInt();
                    System.out.println("Podaj id obrazku: ");
                    int imageId = scanner.nextInt();

                    channel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build();
                    WriteRecordRequest writeRequest = WriteRecordRequest.newBuilder().setName(name).setAge(age).setImageId(imageId).build();
                    bStub = ServiceNameGrpc.newBlockingStub(channel);
                    WriteRecordResponse writeResponse = bStub.writeRecord(writeRequest);

                    boolean success = writeResponse.getSuccess();
                    if (success) {
                        System.out.println("Dodano nową osobę\n");
                    } else {
                        System.out.println("Nie udało się dodać osoby\n");
                    }

                    channel.shutdown();
                    break;
                case 2:
                    System.out.println("Podaj id osoby: ");
                    int id = scanner.nextInt();

                    channel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build();
                    ReadRecordRequest readRequest = ReadRecordRequest.newBuilder().setRecordId(id).build();
                    bStub = ServiceNameGrpc.newBlockingStub(channel);
                    ReadRecordResponse readResponse = bStub.readRecord(readRequest);

                    System.out.println(readResponse.getRecordData());
                    channel.shutdown();

                    break;
                case 3:
                    channel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build();
                    DisplayAllRecordsRequest displayAllRequest = DisplayAllRecordsRequest.newBuilder().build();
                    bStub = ServiceNameGrpc.newBlockingStub(channel);
                    DisplayAllRecordsResponse response = bStub.displayAllRecords(displayAllRequest);

                    System.out.println("Wszytkie osoby: ");
                    System.out.println(response.getRecordData());
                    channel.shutdown();
                    break;
                case 4:
                    System.out.println("Podaj wiek: ");
                    int ageToSearch = scanner.nextInt();

                    SearchRecordsRequest searchRequest = SearchRecordsRequest.newBuilder().setAge(ageToSearch).build();
                    channel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build();
                    nonbStub = ServiceNameGrpc.newStub(channel);
                    nonbStub.searchRecords(searchRequest, new SearchObs());

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    break;
                case 5:
                    System.out.println("Podaj nazwę obrazka: ");
                    String fileName = scanner.next();

                    String imagePath = CLIENT_IMAGE_DIRECTORY + fileName;
                    System.out.println(imagePath);
                    channel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build();
                    nonbStub = ServiceNameGrpc.newStub(channel);

                    StreamObserver<UploadImageRequest> strReqObserver = nonbStub.uploadImage(
                            new StreamUploadImageObs());

                    try {
                        FileInputStream fileInputStream = new FileInputStream(imagePath);
                        BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);

                        byte[] buffer = new byte[128];
                        int bytesRead;
                        while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                            strReqObserver.onNext(UploadImageRequest.newBuilder()
                                            .setImageName(fileName)
                                            .setChunk(ByteString.copyFrom(buffer, 0, bytesRead))
                                            .build());
                        }

                        strReqObserver.onCompleted();
                        Thread.sleep(1000);
                        bufferedInputStream.close();
                        fileInputStream.close();
                    } catch (Exception e) {
                        System.out.println("Nie udało się otworzyć pliku");
                        break;
                    }

                    channel.shutdown();
                    break;
                case 6:
                    channel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build();
                    DisplayAllImagesRequest displayAllImagesRequest = DisplayAllImagesRequest.newBuilder().build();
                    bStub = ServiceNameGrpc.newBlockingStub(channel);
                    DisplayAllImagesResponse imagesResponse = bStub.displayAllImages(displayAllImagesRequest);

                    System.out.println("Wszytkie obrazki: ");
                    System.out.println(imagesResponse.getImagesData());
                    channel.shutdown();
                    break;
                case 7:
                    System.out.println("Podaj nazwę obrazka: ");
                    String imageName = scanner.next();

                    channel = ManagedChannelBuilder.forAddress(address, port).usePlaintext().build();
                    nonbStub = ServiceNameGrpc.newStub(channel);
                    DownloadImageRequest downloadRequest = DownloadImageRequest.newBuilder().setImageName(imageName).build();
                    nonbStub.downloadImage(downloadRequest, new StreamDownloadImageObs(imageName));

                    channel.shutdown();
                    break;
                case 8:
                    System.out.println("Exiting...");
                    System.exit(0);
                    break;
                default:
                    System.out.println("Invalid choice");
            }
        }
    }
}

